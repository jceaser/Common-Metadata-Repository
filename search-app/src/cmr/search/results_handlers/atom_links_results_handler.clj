(ns cmr.search.results-handlers.atom-links-results-handler
  "Handles the ATOM links results format and related functions.
  This is used by granule search in atom format where the atom links in the parent collection
  that are not browse type should be included in the granule atom links as inherited.
  This handles the retrieval of collection atom links by collection concept ids."
  (:require

   [cheshire.core :as json]
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.search.es-index :as elastic-search-index]
   [cmr.elastic-utils.search.es-results-to-query-results :as elastic-results]
   [cmr.elastic-utils.search.query-execution :as qe]))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :atom-links]
  [concept-type query]
  ["concept-id"
   "atom-links"])

(defmethod elastic-results/elastic-result->query-result-item [:collection :atom-links]
  [context query elastic-result]
  (let [{concept-id :_id
         {atom-links :atom-links} :_source} elastic-result
        atom-links (map #(json/decode % true) atom-links)]
    [concept-id atom-links]))

(defn find-collection-atom-links
  "Returns a mapping of collection-concept-ids and its atom links for the given collection-concept-ids"
  [context collection-concept-ids]
  (if (seq collection-concept-ids)
    (let [collection-links-query (qm/query {:concept-type :collection
                                            :condition (qm/string-conditions :concept-id collection-concept-ids true)
                                            :page-size :unlimited
                                            :result-format :atom-links})
          result (qe/execute-query context collection-links-query)]
      (into {} (:items result)))
    {}))
