(ns cmr.system-int-test.search.granule-search-index-name-test
  "Integration tests for searching granules based on the configured index-names.
  This tests that the cmr-search-app and cmr-indexer-app will both put and find data in the correct granule index."
  (:require [clojure.test :refer :all]
            [cmr.common.config :as config]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(defn set-config-then-reset-fixture
  [f]
  (try
    ;; This set up the environment variable that configures which collection will
    ;; have its own granule index. This is only for working in the REPL.
    ;; When this test is run in CI the set variable will not influence the cmr-search-app
    ;; or cmr-indexer-app which are running in a separate process.
    ;; The CI script must set this environment variable to make those work.
    (config/set-config-value! :colls-with-separate-indexes "C1-SEP_PROV1,C2-SEP_PROV1")
    (ingest/reset)
    (ingest/create-provider "provguid1" "SEP_PROV1")
    (f)
    (finally
      (ingest/reset))))

(use-fixtures :each set-config-then-reset-fixture)

(deftest search-on-index-names
  (let [coll1 (d/ingest "SEP_PROV1" (dc/collection {:short-name "ShortOne"
                                                    :concept-id "C1-SEP_PROV1"}))
        coll2 (d/ingest "SEP_PROV1" (dc/collection {:short-name "ShortTwo"
                                                    :concept-id "C2-SEP_PROV1"}))
        coll3 (d/ingest "SEP_PROV1" (dc/collection {}))
        coll4 (d/ingest "SEP_PROV1" (dc/collection {:short-name "ShortThree"}))
        gran1 (d/ingest "SEP_PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))
        gran2 (d/ingest "SEP_PROV1" (dg/granule coll1 {:granule-ur "Granule2"}))
        gran3 (d/ingest "SEP_PROV1" (dg/granule coll1 {:granule-ur "Granule3"}))
        gran4 (d/ingest "SEP_PROV1" (dg/granule coll2 {:granule-ur "Granule4"}))
        gran5 (d/ingest "SEP_PROV1" (dg/granule coll3 {:granule-ur "Granule5"}))
        gran6 (d/ingest "SEP_PROV1" (dg/granule coll4 {:granule-ur "Granule6"}))]

    (index/wait-until-indexed)

    (testing "search for granules in separate collection index."
      (is (d/refs-match?
            [gran1 gran4]
            (search/find-refs :granule {"granule-ur[]" ["Granule1" "Granule4"]}))))
    (testing "search for granules in separate collection index with collection identifier."
      (is (d/refs-match?
            [gran1]
            (search/find-refs :granule {:short-name "ShortOne"
                                        "granule-ur[]" ["Granule1" "Granule4"]}))))
    (testing "search for granules in multiple collection indexes with collection identifier."
      (is (d/refs-match?
            [gran1 gran2 gran3 gran4]
            (search/find-refs :granule {"short-name[]" ["ShortOne" "ShortTwo"]}))))
    (testing "search for granules in multiple collection indexes with collection identifier and small_collections."
      (is (d/refs-match?
            [gran4 gran6]
            (search/find-refs :granule {"short-name[]" ["ShortTwo" "ShortThree"]}))))
    (testing "search for granules in small_collections index."
      (is (d/refs-match?
            [gran5]
            (search/find-refs :granule {:granule-ur "Granule5"}))))
    (testing "search for granules in small_collections index with collection identifier."
      (is (d/refs-match?
            [gran6]
            (search/find-refs :granule {:short-name "ShortThree"}))))))

