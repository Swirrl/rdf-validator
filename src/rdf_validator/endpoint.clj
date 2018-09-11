(ns rdf-validator.endpoint
  (:require [grafter.rdf.repository :as repo]))

(defn- create-dataset [graphs]
  (if-let [restriction (seq (map str graphs))]
    (repo/make-restricted-dataset :default-graph restriction :named-graphs restriction)))

(defn create-endpoint [repo graphs]
  {:repository repo :dataset (create-dataset graphs)})

(defn prepare-query [{:keys [repository dataset] :as endpoint} query-string]
  (repo/prepare-query repository query-string dataset))

(def dataset :dataset)
(def repository :repository)
