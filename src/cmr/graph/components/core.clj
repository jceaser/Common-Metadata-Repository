(ns cmr.graph.components.core
  (:require
    [cmr.graph.components.config :as config]
    [cmr.graph.components.elastic :as elastic]
    [cmr.graph.components.httpd :as httpd]
    [cmr.graph.components.logging :as logging]
    [cmr.graph.components.neo4j :as neo4j]
    [cmr.process.manager.components.docker :as docker]
    [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Common Configuration Components   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def cfg
  {:config (config/create-component)})

(def log
  {:logging (component/using
             (logging/create-component)
             [:config])})

(def neo4jd
  {:neo4jd (component/using
           (docker/create-component
             :neo4j
             {:image-id "neo4j:3.3.3"
              :ports ["127.0.0.1:7687:7687"
                      "127.0.0.1:7474:7474"
                      "127.0.0.1:7473:7473"]
              :env ["NEO4J_AUTH=none"]
              :volumes [(str (System/getProperty "user.dir") "/data/neo4j:/data")]
              :container-id-file "/tmp/cmr-graph-neo4j-container-id"})
           [:config :logging])})

(def neo4j
  {:neo4j (component/using
           (neo4j/create-component)
           [:config :logging :neo4jd])})


(def httpd
  {:httpd (component/using
           (httpd/create-component)
           [:config :logging :neo4j])})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Initializations   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn initialize-bare-bones
  []
  (component/map->SystemMap
    (merge cfg
           log
           neo4jd
           neo4j)))

(defn initialize-with-web
  []
  (component/map->SystemMap
    (merge cfg
           log
           neo4jd
           neo4j
           httpd)))

(def init-lookup
  {:basic #'initialize-bare-bones
   :web #'initialize-with-web})

(defn init
  ([]
    (init :web))
  ([mode]
    ((mode init-lookup))))
