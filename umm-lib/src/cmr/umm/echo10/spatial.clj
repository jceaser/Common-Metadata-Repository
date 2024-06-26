(ns cmr.umm.echo10.spatial
  "Contains functions for convert spatial to and parsing from ECHO10 XML."
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.spatial.point :as point]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.line-string :as line-string]
   [cmr.spatial.polygon :as poly]
   [cmr.umm.umm-spatial :as umm-s]
   [cmr.umm.umm-granule :as granule]
   [cmr.common.util :as util]))

(defmulti parse-geometry
  "Parses a geometry element based on the tag of the element."
  (fn [element]
    (:tag element)))

(defmethod parse-geometry :GPolygon
  [element]
  (let [outer-ring (parse-geometry (cx/element-at-path element [:Boundary]))
        holes (map parse-geometry (cx/elements-at-path element [:ExclusiveZone :Boundary]))]
    (poly/polygon (cons outer-ring holes))))

(defmethod parse-geometry :Point
  [element]
  (let [lon (cx/double-at-path element [:PointLongitude])
        lat (cx/double-at-path element [:PointLatitude])]
    (point/point lon lat)))

(defmethod parse-geometry :Line
  [element]
  (line-string/line-string (map parse-geometry (:content element))))

(defmethod parse-geometry :Boundary
  [element]
  (let [points (reverse (map parse-geometry (:content element)))
        points (concat points [(first points)])]
    (umm-s/ring points)))

(defmethod parse-geometry :BoundingRectangle
  [element]
  (let [west (cx/double-at-path element [:WestBoundingCoordinate])
        east (cx/double-at-path element [:EastBoundingCoordinate])
        north (cx/double-at-path element [:NorthBoundingCoordinate])
        south (cx/double-at-path element [:SouthBoundingCoordinate])]
    (mbr/mbr west north east south)))

(def geometry-tags
  "The list of geometry tags in the geometry element that are actual spatial area elements"
  #{:GPolygon :Point :Line :Boundary :BoundingRectangle})

(defn geometry-element->geometries
  "Converts a Geometry element into a sequence of spatial geometry objects"
  [geom-elem]
  (map parse-geometry (filter (comp geometry-tags :tag) (:content geom-elem))))

(def key->orbit-direction
  "Mapping of keys to orbit direction stirngs."
  {:asc "A"
   :desc "D"})

(def orbit-direction->key
  "Mapping of oribit direction strings to keywords."
  {"A" :asc
   "a" :asc
   "D" :desc
   "d" :desc})

(defn xml-elem->Orbit
  "Returns a UMM Orbit record from a parsed Orbit XML structure"
  [orbit]
  (granule/map->Orbit {:ascending-crossing (cx/double-at-path orbit [:AscendingCrossing])
                       :start-lat (cx/double-at-path orbit [:StartLat])
                       :start-direction (orbit-direction->key (cx/string-at-path orbit [:StartDirection]))
                       :end-lat (cx/double-at-path orbit [:EndLat])
                       :end-direction (orbit-direction->key (cx/string-at-path orbit [:EndDirection]))}))

(defn generate-orbit-xml
  [orbit]
  (when-let [{:keys [ascending-crossing start-lat start-direction end-lat end-direction]}
             orbit]
    (xml/element :Orbit {}
                 (xml/element :AscendingCrossing {} (util/double->string ascending-crossing))
                 (xml/element :StartLat {} (util/double->string start-lat))
                 (xml/element :StartDirection {} (key->orbit-direction start-direction))
                 (xml/element :EndLat {} (util/double->string end-lat))
                 (xml/element :EndDirection {} (key->orbit-direction end-direction)))))

(defprotocol ShapeToXml
  "Protocol for converting a shape into XML."

  (shape-to-xml
    [shape]
    "Converts the shape into a XML struct element"))

(defn generate-geometry-xml
  [geometries]
  (xml/element :Geometry {}
               (map shape-to-xml geometries)))


(defn- ring-to-xml
  [ring]
  (xml/element :Boundary {}
               (map shape-to-xml
                    ;; Points must be specified in clockwise order and not closed.
                    (-> (:points ring)
                        ; drop first point since last point will match
                        drop-last
                        ; counter clocwise to clockwise
                        reverse))))

(extend-protocol ShapeToXml
  cmr.spatial.point.Point
  (shape-to-xml
    [{:keys [lon lat]}]
    (xml/element :Point {}
                 (xml/element :PointLongitude {} (util/double->string lon))
                 (xml/element :PointLatitude {} (util/double->string lat))))

  cmr.spatial.mbr.Mbr
  (shape-to-xml
    [{:keys [west north east south]}]
    (xml/element :BoundingRectangle {}
                 (xml/element :WestBoundingCoordinate {} (util/double->string west))
                 (xml/element :NorthBoundingCoordinate {} (util/double->string north))
                 (xml/element :EastBoundingCoordinate {} (util/double->string east))
                 (xml/element :SouthBoundingCoordinate {} (util/double->string south))))

  cmr.spatial.line_string.LineString
  (shape-to-xml
    [{:keys [points]}]
    (xml/element :Line {} (map shape-to-xml points)))

  cmr.spatial.geodetic_ring.GeodeticRing
  (shape-to-xml
    [ring]
    (ring-to-xml ring))

  cmr.spatial.cartesian_ring.CartesianRing
  (shape-to-xml
    [ring]
    (ring-to-xml ring))

  cmr.umm.umm_spatial.GenericRing
  (shape-to-xml
    [ring]
    (ring-to-xml ring))

  cmr.spatial.polygon.Polygon
  (shape-to-xml
    [{:keys [rings]}]
    (let [boundary (first rings)
          holes (seq (rest rings))]
      (xml/element :GPolygon {}
                   (shape-to-xml boundary)
                   (when holes
                     (xml/element :ExclusiveZone {} (map shape-to-xml holes)))))))

