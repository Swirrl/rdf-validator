(ns rdf-validator.endpoint
  (:require [grafter-2.rdf4j.repository :as repo]
            [grafter-2.rdf4j.io :as rdf]
            [grafter-2.rdf.protocols :as pr]
            [grafter-2.rdf4j.formats :as formats]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import [java.net URISyntaxException URI]
           [java.io File]))

(defn supported-file? [f]
  (boolean (formats/filename->rdf-format f)))

(defn file->repository [^File f]
  (if (.isDirectory f)
    (let [r (repo/sail-repo)]
      (with-open [conn (repo/->connection r)]
        (log/info "Creating repository from directory: " (.getAbsolutePath f))
        (loop [[file & files] (.listFiles f)]
          (when file
            (if (.isDirectory file)
              (recur (concat files (.listFiles file)))
              (do
                (if (supported-file? file)
                  (pr/add conn (rdf/statements file)))
                (recur files))))))
      r)
    (do
      (log/info "Creating repository from file: " (.getAbsolutePath f))
      (repo/fixture-repo f))))

(defmulti uri->repository (fn [^URI uri] (some-> (.getScheme uri) keyword)))

(defn- create-sparql-repo [uri]
  (log/info "Creating SPARQL repository: " (str uri))
  (repo/sparql-repo (str uri)))

(defmethod uri->repository :http [uri]
  (create-sparql-repo uri))

(defmethod uri->repository :https [uri]
  (create-sparql-repo uri))

(defmethod uri->repository :file [uri]
  (file->repository (io/file uri)))

(defmethod uri->repository :default [uri]
  (file->repository (io/file (str uri))))

(defn parse-repository
  "Parses a sesame repository instance from a string representation"
  [repository-str]
  (try
    (uri->repository (URI. repository-str))
    (catch URISyntaxException ex
      (file->repository (io/file repository-str)))))

(defn- create-dataset [graphs]
  (if-let [restriction (seq (map str graphs))]
    (repo/make-restricted-dataset :default-graph restriction :named-graphs restriction)))

(defn create-endpoint [repo graphs]
  {:repository repo :dataset (create-dataset graphs)})

(def dataset :dataset)
(def repository :repository)
