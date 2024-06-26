(ns cmr.elasticsearch.plugins.spatial.factory.lfactory
  (:import
   (cmr.elasticsearch.plugins SpatialScript)
   (java.util Map)
   (org.apache.logging.log4j LogManager)
   (org.elasticsearch.script DocReader)
   (org.elasticsearch.common.xcontent.support XContentMapValues)
   (org.elasticsearch.search.lookup SearchLookup))
  (:require
   [clojure.string :as string]
   [cmr.spatial.relations :as relations]
   [cmr.spatial.serialize :as srl])
  (:gen-class
   :name cmr.elasticsearch.plugins.SpatialScriptLeafFactory
   :implements [org.elasticsearch.script.FilterScript$LeafFactory]
   :constructors {[java.util.Map org.elasticsearch.search.lookup.SearchLookup] []}
   :init init
   :state data))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                    Begin factory helper functions                         ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def parameters
  "The parameters to the Spatial script"
  [:ords :ords-info])

(defn- extract-params
  "Extracts the parameters from the params map given in the script."
  [script-params]
  (when script-params
    (into {} (for [param parameters]
               [param (XContentMapValues/nodeStringValue
                        (get script-params (name param)) nil)]))))

(defn- assert-required-parameters
  "Asserts that all the parameters are supplied or it throws an exception."
  [params]
  (when-not (every? params parameters)
    (throw (IllegalArgumentException.
             (str "Missing one or more of required parameters: "
                  (clojure.string/join parameters ", "))))))

(defn- convert-params
  "Convert the comma separated string into a vector of integers."
  [[k v]]
  [k (map #(Integer. ^String %) (string/split v #","))])

(defn- params->spatial-shape
  [params]
  (let [{:keys [ords-info ords]} (->> params
                                      (map convert-params)
                                      (into {}))]
    (first (srl/ords-info->shapes ords-info ords))))

(defn get-intersects-fn [script-params]
  (let [params (extract-params script-params)
        shape (params->spatial-shape params)]
    (assert-required-parameters params)
    (try
      (relations/shape->intersects-fn shape)
      (catch Exception e
        (.error (LogManager/getLogger "cmr_spatial_lfactory") (format "Unable to create intersects function for shapes [%s]" (pr-str shape)) e)
        (throw (ex-info "An exception occurred creating intersects-fn" {:shape shape :params params} e))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                    End factory helper functions                           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                    Begin leaf factory functions                           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(import 'cmr.elasticsearch.plugins.SpatialScriptLeafFactory)

(defn- -init [^Map params ^SearchLookup lookup]
  [[] {:params params
       :lookup lookup}])

(defn -newInstance [^SpatialScriptLeafFactory this ^DocReader doc-reader]
  (let [^Map params (-> this .data :params)]
    (SpatialScript.
     ^Object (get-intersects-fn params)
     ^Map params
     ^SearchLookup (-> this .data :lookup)
     ^DocReader doc-reader)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                    End leaf factory functions                             ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
