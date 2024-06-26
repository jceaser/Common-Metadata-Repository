(ns cmr.ingest.validation.platform-validation
  "Provides functions to validate the platforms during collection update"
  (:require
   [clojure.set :as set]
   [cmr.common.util :as util]
   [cmr.common-app.data.humanizer-alias-cache :as humanizer-alias-cache]))

(defn deleted-platform-searches
  "Returns granule searches for deleted platforms. We should not delete platforms in a collection
  that are still referenced by existing granules. This function builds the search parameters
  for identifying such invalid deletions."
  [context concept-id concept prev-concept]
  (let [platform-alias-map (humanizer-alias-cache/get-non-humanized-source-to-aliases-map context "platform")
        current-platforms (map :ShortName (:Platforms concept))
        previous-platforms (map :ShortName (:Platforms prev-concept))
        platform-aliases (mapcat #(get platform-alias-map %) (map util/safe-uppercase current-platforms))
        ;; Only the deleted ones that are not part of the platform-aliases need to be validated.
        deleted-platform-names (set/difference
                                (set (map util/safe-lowercase previous-platforms))
                                (set (map util/safe-lowercase (concat current-platforms platform-aliases))))]
    (for [name deleted-platform-names]
      {:params {"platform[]" name
                :collection-concept-id concept-id
                "options[platform][exclude_collection]" "true"}
       :error-msg (format (str "Collection Platform [%s] is referenced by existing"
                               " granules, cannot be removed.") name)})))
