(ns cmr.spatial.encoding.gmd
  "Functions for encoding and decoding between spatial geometries and
  gmd:geographicElement elements.

  GMD is the Geographic MetaData schema for describing geographic
  information in ISO XML documents.

  see: http://www.isotc211.org/schemas/2005/gmd/"
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.spatial.encoding.gml :as gml]
   [cmr.spatial.mbr :as mbr]))

(declare decode-geo-content)

;; Interface

(defmulti encode
  "Returns a gmd:geographicElement xml element from a spatial geometry."
  type)

(defn decode
  "Returns spatial geometry parsed from a gmd:geographicElement xml element."
  [gmd-geo-element]
  (-> gmd-geo-element :content first decode-geo-content))

;; Implementations

(defmulti decode-geo-content
  "Decode the content of a gmd:geographicElement"
  :tag)

(defmethod encode cmr.spatial.mbr.Mbr
  [geometry]
  (let [{:keys [west north east south]} geometry
        gen-point-fn (fn [tag content]
                       (xml/element tag {}
                                  (xml/element :gco:Decimal {} content)))]
    (xml/element :gmd:geographicElement {}
               (xml/element :gmd:EX_GeographicBoundingBox {:id (str "geo-" (java.util.UUID/randomUUID))}
                          (xml/element :gmd:extentTypeCode {}
                                     (xml/element :gco:Boolean {} 1))
                          (gen-point-fn :gmd:westBoundLongitude west)
                          (gen-point-fn :gmd:eastBoundLongitude east)
                          (gen-point-fn :gmd:southBoundLatitude south)
                          (gen-point-fn :gmd:northBoundLatitude north)))))

(defmethod decode-geo-content :EX_GeographicBoundingBox
  [bounding-box-elem]
  (let [parse #(cx/double-at-path bounding-box-elem [% :Decimal])]
    (apply mbr/mbr (map parse [:westBoundLongitude :northBoundLatitude
                               :eastBoundLongitude :southBoundLatitude]))))

;; GMD treats everything else (points, lines, polygons) as polygons

(defmethod encode :default
  [polygon]
  (xml/element
   :gmd:geographicElement {}
   (xml/element
    :gmd:EX_BoundingPolygon {}
    (xml/element :gmd:extentTypeCode {}
               (xml/element :gco:Boolean {} 1))
    (xml/element :gmd:polygon {}
               (gml/encode polygon)))))

(defmethod decode-geo-content :EX_BoundingPolygon
  [bounding-polygon-element]
  ;; EXBoundingPolygon elements contain a gmd:polygon which contains a
  ;; single gml geometry element
  (let [polygons (cx/elements-at-path bounding-polygon-element [:polygon])]
    (map #(-> % :content first gml/decode) polygons)))
