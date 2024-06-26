(ns cmr.search.results-handlers.provider-holdings
  "Defines functions to generate xml and json string for provider-holdings"
  (:require 
    [cheshire.core :as json] 
    [clojure.data.csv :as csv]
    [clojure.data.xml :as xml]
    [clojure.set :as set]
    [cmr.common.services.errors :as service-errors])
  (:import
    [java.io StringWriter]))

(defmulti provider-holdings->string
  "Returns the string representation of the given provider-holdings"
  (fn [result-format provider-holdings options]
    result-format))

(defmulti provider-holding->xml-elem
  "Returns the XML element of the given provider-holding"
  (fn [echo-compatible? provider-holding]
    echo-compatible?))

(defmethod provider-holding->xml-elem false
  [echo-compatible? provider-holding]
  (let [{:keys [entry-title concept-id provider-id granule-count]} provider-holding]
    (xml/element :provider-holding {}
               (xml/element :entry-title {} entry-title)
               (xml/element :concept-id {} concept-id)
               (xml/element :granule-count {} granule-count)
               (xml/element :provider-id {} provider-id))))

(defmethod provider-holding->xml-elem true
  [echo-compatible? provider-holding]
  (let [{:keys [entry-title concept-id provider-id granule-count]} provider-holding]
    (xml/element :provider-holding {}
               (xml/element :dataset_id {} entry-title)
               (xml/element :echo_collection_id {} concept-id)
               (xml/element :granule_count {} granule-count)
               (xml/element :provider_id {} provider-id))))

(defmethod provider-holdings->string :xml
  [result-format provider-holdings options]
  (let [{:keys [echo-compatible?]} options]
    (xml/emit-str
      (xml/element :provider-holdings {:type "array"}
                 (map (partial provider-holding->xml-elem echo-compatible?) provider-holdings)))))

(def CSV_HEADER
  ["Provider Id", "Entry Title", "Concept Id", "Granule Count"])

(defn- provider-holding->csv
  [provider-holding]
  (map provider-holding [:provider-id :entry-title :concept-id :granule-count]))

(defmethod provider-holdings->string :csv
  [result-format provider-holdings options]
  (let [rows (cons CSV_HEADER
                   (map provider-holding->csv provider-holdings))
        string-writer (StringWriter.)]
    (csv/write-csv string-writer rows)
    (str string-writer)))

(defn- cmr-provider-holding->echo-provider-holding
  "Returns the given provider holding in ECHO format"
  [provider-holding]
  (set/rename-keys provider-holding {:entry-title :dataset_id
                                     :concept-id :echo_collection_id
                                     :granule-count :granule_count
                                     :provider-id :provider_id}))

(defmethod provider-holdings->string :json
  [result-format provider-holdings options]
  (let [{:keys [echo-compatible?]} options
        provider-holdings (if echo-compatible?
                            (map cmr-provider-holding->echo-provider-holding provider-holdings)
                            provider-holdings)]
    (json/generate-string provider-holdings)))

(defmethod provider-holdings->string :default
  [result-format provider-holdings options]
  (let [result-format (if-let [format (:format result-format)]
                        (name format)
                        (name result-format))]
    (service-errors/throw-service-error
      :bad-request (format "Unsupported format: %s on the provider holdings endpoint." result-format))))
