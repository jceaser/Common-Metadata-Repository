(ns cmr.search.services.query-execution.tags-results-feature
  "This enables the :include-tags feature for collection search results. When it is enabled
  collection search results will include the list of tags that are associated with the collection."
  (:require
   [clojure.string :as string]
   [cmr.common.util :as util]
   [clojure.edn :as edn]
   [cmr.elastic-utils.search.es-query-to-elastic :as q2e]
   [cmr.elastic-utils.search.query-execution :as query-execution]))

(def stored-tags-field
  "name of the elasticsearch collection mapping field that stores the tags info"
  "tags-gzip-b64")

(defn- escape-wildcards
  [value]
  (-> value
      ;; Escape * and ?
      (string/replace "*" ".*")
      (string/replace "?" ".?")))

(defn- match-patterns?
  "Returns true if the value matches one of the strings that can be parsed as regex."
  [value regex-strs]
  (let [patterns (map #(re-pattern (q2e/escape-query-string (escape-wildcards %))) regex-strs)]
    (when value (some #(re-find % value) patterns))))

(defmethod query-execution/post-process-query-result-feature :tags
  [context query elastic-results query-results feature]
  (let [include-tags (get-in query [:result-options :tags])
        matched-tags (fn [values]
                       (when-let [tags (seq (filter #(match-patterns? (first %) include-tags) values))]
                         (into {} tags)))]
    ;; only keep the tags that matches the include-tags result options
    (util/update-in-each query-results
                         [:items]
                         (fn [item]
                           (update item :tags matched-tags)))))

(defn collection-elastic-result->tags
  "Returns the stored tags from collection search elastic-result"
  [result]
  (some-> result
          :_source
          :tags-gzip-b64
          util/gzip-base64->string
          edn/read-string))
