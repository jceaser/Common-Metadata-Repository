;; WARNING: This file was generated from umm-sub-json-schema.json. Do not manually modify.
(ns cmr.umm-spec.models.umm-subscription-models
   "Defines UMM-Sub clojure records."
 (:require [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))

(defrecord UMM-Sub
  [
   ;; The name of a subscription.
   Name

   ;; The userid of the subscriber.
   SubscriberId

   ;; The email address of the subscriber.
   EmailAddress

   ;; The collection concept id of the granules subscribed.
   CollectionConceptId

   ;; The search query for the granules that matches the subscription.
   Query
  ])
(record-pretty-printer/enable-record-pretty-printing UMM-Sub)