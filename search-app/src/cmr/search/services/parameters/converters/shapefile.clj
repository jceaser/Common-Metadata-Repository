(ns cmr.search.services.parameters.converters.shapefile
  "Contains parameter converters for shapefile parameter"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.log :refer [debug info]]
   [cmr.common.mime-types :as mt]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.params :as p]
   [cmr.common.services.errors :as errors]
   [cmr.search.models.query :as qm]
   [cmr.search.services.parameters.converters.geojson :as geojson]
   [cmr.search.services.parameters.converters.geometry :as geo]
   [cmr.common.util :as util])
  (:import
   (java.io BufferedInputStream File FileReader FileOutputStream FileInputStream)
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)
   (java.net URL)
   (java.util ArrayList HashMap)
   (java.util.zip ZipFile ZipInputStream)
   (org.apache.commons.io FilenameUtils)
   (org.geotools.data DataStoreFinder FileDataStoreFinder Query)
   (org.geotools.data.simple SimpleFeatureSource)
   (org.geotools.data.geojson GeoJSONDataStore)
   (org.geotools.geometry.jts JTS)
   (org.geotools.kml.v22 KMLConfiguration KML)
   (org.geotools.referencing CRS)
   (org.geotools.util URLs)
   (org.geotools.xsd Parser StreamingParser PullParser)
   (org.locationtech.jts.geom Geometry)
   (org.opengis.feature.simple SimpleFeature)
   (org.opengis.feature.type Name)))

(def EPSG-4326-CRS
  "The CRS object for WGS 84"
  (CRS/decode "EPSG:4326" true))

(defconfig enable-shapefile-parameter-flag
  "Flag that indicates if we allow spatial searching by shapefile."
  {:default false :type Boolean})

(defconfig max-shapefile-features
  "The maximum number of feature a shapefile can have"
  {:default 500 :type Long})

(defconfig max-shapefile-points
  "The maximum number of points a shapefile can have"
  {:default 5000 :type Long})

(defn unzip-file
  "Unzip a file (of type File) into a temporary directory and return the directory path as a File"
  [source]
  (let [target-dir (Files/createTempDirectory "Shapes" (into-array FileAttribute []))]
    (try
      (with-open [zip (ZipFile. source)]
        (let [entries (enumeration-seq (.entries zip))
              target-file #(File. (.toString target-dir) (str %))]
          (doseq [entry entries :when (not (.isDirectory ^java.util.zip.ZipEntry entry))
                  :let [f (target-file entry)]]
            (debug (format "Zip file entry: [%s]" (.getName entry)))
            (io/copy (.getInputStream zip entry) f))))
      (.toFile target-dir)
      (catch Exception e
        (.delete (.toFile target-dir))
        (errors/throw-service-error :bad-request (str "Error while uncompressing zip file: " (.getMessage e)))))))

(defn find-shp-file
  "Find the .shp file in the given directory (File) and return it as a File"
  [dir]
  (let [files (file-seq dir)]
    (first (filter #(= "shp" (FilenameUtils/getExtension (.getAbsolutePath %))) files))))

(defn geometry->conditions
  "Get one or more conditions for the given Geometry. This will only
  return more than one condition if the Geometry is a GeometryCollection.
  The `options` map can be used to provided additional information."
  [^Geometry geometry options]
  (let [num-geometries (.getNumGeometries geometry)]
    (debug (format "NUM SUB GEOMETRIES: [%d]" num-geometries))
    (for [index (range 0 num-geometries)
          :let [sub-geometry (.getGeometryN geometry index)]]
      (geo/geometry->condition sub-geometry options))))

(defn geometry-point-count
  "Get the number of points in the given Geometry"
  [^Geometry geometry]
  (let [num-geometries (.getNumGeometries geometry)
        all-geometries  (for [index (range 0 num-geometries)
                              :let [sub-geometry (.getGeometryN geometry index)]]
                          sub-geometry)]
    (reduce (fn [count geometry] (+ count (.getNumPoints geometry))) 0 all-geometries)))

(defn transform-to-epsg-4326
  "Transform the geometry to WGS84 CRS if is not already"
  [geometry src-crs]
  ;; if the source CRS is defined and not already WGS84 then transform the geometry to WGS84
  (if (and src-crs
           (not (= (.getName src-crs) (.getName EPSG-4326-CRS))))
    (let [src-crs-name (.getName src-crs)]
      (debug (format "Source CRS: [%s]" src-crs-name))
      (debug (format "Source axis order: [%s]" (CRS/getAxisOrder src-crs)))
      (debug (format "Destination CRS: [%s]" (.getName EPSG-4326-CRS)))
      (debug (format "Destination axis order: [%s]" (CRS/getAxisOrder EPSG-4326-CRS)))
      ; If we find a transform use it to transform the geometry, 
      ; otherwise send an error message
      (if-let [transform (try
                           (CRS/findMathTransform src-crs EPSG-4326-CRS false)
                           (catch Exception e))]
        (let [new-geometry (JTS/transform geometry transform)]
          (debug (format "New geometry: [%s" new-geometry))
          new-geometry)
        (errors/throw-service-error :bad-request (format "Cannot transform source CRS [%s] to WGS 84" src-crs-name))))
    geometry))

(defn feature->conditions
  "Process the contents of a Feature to return query conditions along with number of points in
  the processed Feature. The `context` map can be used to pass along additional info."
  [feature context]
  (let [crs (when (.getDefaultGeometryProperty feature)
              (-> feature .getDefaultGeometryProperty .getDescriptor .getCoordinateReferenceSystem))
        properties (.getProperties feature)
        _ (doseq [p properties] (debug (.getName p)) (debug (.getValue p)))
        geometry-props (filter (fn [p] (geo/geometry? (.getValue p))) properties)
        _ (debug (format "Found [%d] geometries" (count geometry-props)))
        geometries (map #(-> % .getValue (transform-to-epsg-4326 crs)) geometry-props)
        _ (debug (format "Transformed [%d] geometries" (count geometries)))
        point-count (apply + (map geometry-point-count geometries))
        conditions (mapcat (fn [g] (geometry->conditions g context)) geometries)]
    (debug (format "CONDITIONS: %s" conditions))
    [conditions point-count]))

(defn error-if
  "Throw a service error with the given message if `f` applied to `item` is true. 
  Otherwise just return `item`. Removes the temporary file/directory `temp-file` first."
  [item f message ^File temp-file]
  (if (f item)
    (do
      (when temp-file (.delete temp-file))
      (errors/throw-service-error :bad-request message))
    item))

(defn esri-shapefile->condition-vec
  "Converts a shapefile to a vector of SpatialConditions"
  [shapefile-info]
  (try
    (let [file (:tempfile shapefile-info)
          ^File temp-dir (unzip-file file)
          shp-file (error-if
                    (find-shp-file temp-dir)
                    nil?
                    "Incomplete shapefile: missing .shp file"
                    temp-dir)
          data-store (FileDataStoreFinder/getDataStore shp-file)
          feature-source (.getFeatureSource data-store)
          features (.getFeatures feature-source)
          feature-count (error-if (.size features)
                                  #(< % 1)
                                  "Shapefile has no features"
                                  temp-dir)
          _ (error-if feature-count
                      #(> % (max-shapefile-features))
                      (format "Shapefile feature count [%d] exceeds the %d feature limit"
                              feature-count
                              (max-shapefile-features))
                      nil)
          _ (debug (format "Found [%d] features" feature-count))
          iterator (.features features)]
      (try
        (loop [conditions [] total-point-count 0]
          (if (.hasNext iterator)
            (let [feature (.next iterator)
                  [feature-conditions num-points] (feature->conditions feature {:boundary-winding :cw})
                  new-point-count (+ total-point-count num-points)]
              (when (> new-point-count (max-shapefile-points))
                (errors/throw-service-error :bad-request
                                            (format "Number of points in shapefile exceeds the limit of %d"
                                                    (max-shapefile-points))))
              (if (> (count feature-conditions) 0)
                (recur (conj conditions (gc/or-conds feature-conditions)) new-point-count)
                (recur conditions total-point-count)))
            conditions))
        (finally (do
                   (.close iterator)
                   (-> data-store .getFeatureReader .close)
                   (.delete temp-dir)))))
    (catch Exception e
      (let [{:keys [type errors]} (ex-data e)]
        (if (and type errors)
          (throw e) ;; This was a more specific service error so just re-throw it
          (errors/throw-service-error :bad-request "Failed to parse shapefile"))))))

(defn geojson->conditions-vec
  "Converts a geojson file to a vector of SpatialConditions"
  [shapefile-info]
  (try
    (let [file (:tempfile shapefile-info)
          _ (geojson/sanitize-geojson file)
          url (URLs/fileToUrl file)
          data-store (GeoJSONDataStore. url)
          feature-source (.getFeatureSource data-store)
          features (.getFeatures feature-source)
          feature-count (error-if (.size features)
                                  #(< % 1)
                                  "GeoJSON has no features"
                                  nil)
          _ (error-if feature-count
                      #(> % (max-shapefile-features))
                      (format "GeoJSON feature count [%d] exceeds the %d feature limit"
                              feature-count
                              (max-shapefile-features))
                      nil)
          _ (debug (format "Found [%d] features" feature-count))
          iterator (.features features)]
      (try
        (loop [conditions [] total-point-count 0]
          (if (.hasNext iterator)
            (let [feature (.next iterator)
                  [feature-conditions num-points] (feature->conditions feature {:hole-winding :cw})
                  new-point-count (+ total-point-count num-points)]
              (when (> new-point-count (max-shapefile-points))
                (errors/throw-service-error :bad-request
                                            (format "Number of points in GeoJSON file exceeds the limit of %d"
                                                    (max-shapefile-points))))
              (if (> (count feature-conditions) 0)
                (recur (conj conditions (gc/or-conds feature-conditions)) new-point-count)
                (recur conditions total-point-count)))
            conditions))
        (finally (do
                   (.close iterator)
                   (-> data-store .getFeatureReader .close)
                   (.delete file)))))
    (catch Exception e
      (let [{:keys [type errors]} (ex-data e)]
        (if (and type errors)
          (throw e) ;; This was a more specific service error so just re-throw it
          (errors/throw-service-error :bad-request "Failed to parse GeoJSON file"))))))

(defn kml->conditions-vec
  "Converts a kml file to a vector of SpatialConditions"
  [shapefile-info]
  (try
    (let [file (:tempfile shapefile-info)
          input-stream (FileInputStream. file)
          parser (PullParser. (KMLConfiguration.) input-stream SimpleFeature)]
      (try
        (loop [conditions [] feature-count 0 total-point-count 0]
          (if-let [feature (.parse parser)]
            (let [[feature-conditions num-points] (feature->conditions feature {})
                  new-point-count (+ total-point-count num-points)]
              (when (> new-point-count (max-shapefile-points))
                (errors/throw-service-error :bad-request
                                            (format "Number of points in KML file exceeds the limit of %d"
                                                    (max-shapefile-points))))
              (if (> (count feature-conditions) 0)
                (recur (conj conditions (gc/or-conds feature-conditions))
                       (+ feature-count 1)
                       new-point-count)
                (recur conditions feature-count total-point-count)))
            (do
              (error-if feature-count
                        #(> % (max-shapefile-features))
                        (format "KML feature count [%d] exceeds the %d feature limit"
                                feature-count
                                (max-shapefile-features))
                        nil)
              conditions)))
        (finally
          (.delete file))))
    (catch Exception e
      (let [{:keys [type errors]} (ex-data e)]
        (if (and type errors)
          (throw e) ;; This was a more specific service error so just re-throw it
          (errors/throw-service-error :bad-request "Failed to parse KML file"))))))

(defn winding-opts
  "Get the opts for a call to `normalize-polygon-winding` based on file type"
  [mime-type]
  (case mime-type
    "application/shapefile+zip" {:boundary-winding :cw}
    "application/vnd.google-earth.kml+xml" {}
    "application/geo+json" {:hole-winding :cw}))

(defn in-memory->conditions-vec
  "Converts a group of features produced by simplification to a vector of SpatialConditions"
  [shapefile-info]
  (try
    (let [^ArrayList features (:tempfile shapefile-info)
          mime-type (:content-type shapefile-info)
          iterator (.iterator features)]
      (loop [conditions []]
        (if (.hasNext iterator)
          (let [feature (.next iterator)
                [feature-conditions _] (feature->conditions feature (winding-opts mime-type))]
            (if (> (count feature-conditions) 0)
              (recur (conj conditions (gc/or-conds feature-conditions)))
              (recur conditions)))
          conditions)))
    (catch Exception e
      (.printstackTrace e))))

(defmulti shapefile->conditions
  "Converts a shapefile to query conditions based on shapefile format"
  (fn [shapefile-info]
    (info (format "SHAPEFILE FORMAT: %s" (:contenty-type shapefile-info)))
    (if (:in-memory shapefile-info)
      :in-memory
      (:content-type shapefile-info))))

;; ESRI shapefiles
(defmethod shapefile->conditions mt/shapefile
  [shapefile-info]
  (let [conditions-vec (esri-shapefile->condition-vec shapefile-info)]
    (gc/or-conds (flatten conditions-vec))))

;; GeoJSON
(defmethod shapefile->conditions mt/geojson
  [shapefile-info]
  (let [conditions-vec (geojson->conditions-vec shapefile-info)]
    (gc/or-conds (flatten conditions-vec))))

;; KML
(defmethod shapefile->conditions mt/kml
  [shapefile-info]
  (let [conditions-vec (kml->conditions-vec shapefile-info)]
    (gc/or-conds (flatten conditions-vec))))

;; Simplfied and stored in memory
(defmethod shapefile->conditions :in-memory
  [shapefile-info]
  (let [conditions-vec (in-memory->conditions-vec shapefile-info)]
    (gc/or-conds (flatten conditions-vec))))

(defmethod p/parameter->condition :shapefile
  [_context concept-type param value options]
  (if (enable-shapefile-parameter-flag)
    (shapefile->conditions value)
    (errors/throw-service-error :bad-request "Searching by shapefile is not enabled")))
