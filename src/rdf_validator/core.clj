(ns rdf-validator.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [grafter.rdf :as rdf]
            [grafter.rdf.repository :as repo])
  (:import [java.net URI URISyntaxException]
           [java.io File]))

(defn file->repository [^File f]
  (if (.isDirectory f)
    (let [r (repo/sail-repo)]
      (println "Creating repository from directory: " (.getAbsolutePath f))
      (doseq [df (.listFiles f)]
        (rdf/add r (rdf/statements df)))
      r)
    (do
      (println "Creating repository from file: " (.getAbsolutePath f))
      (repo/fixture-repo f))))

(defmulti uri->repository (fn [^URI uri] (some-> (.getScheme uri) keyword)))

(defn- create-sparql-repo [uri]
  (println "Creating SPARQL repository: " (str uri))
  (repo/sparql-repo (str uri)))

(defmethod uri->repository :http [uri]
  (create-sparql-repo uri))

(defmethod uri->repository :https [uri]
  (create-sparql-repo uri))

(defmethod uri->repository :file [uri]
  (file->repository (io/file uri)))

(defmethod uri->repository :default [uri]
  (file->repository (io/file (str uri))))

(defn- parse-endpoint [endpoint-str]
  (try
    (uri->repository (URI. endpoint-str))
    (catch URISyntaxException ex
      (file->repository (io/file endpoint-str)))))

(def cli-options
  [["-s" "--suite SUITE" "Test suite file or directory"
    :default []
    :parse-fn io/file
    :validate [(fn [f] true) "File does not exist"]
    :assoc-fn (fn [m k v] (update-in m [k] conj v))]
   ["-e" "--endpoint ENDPOINT" "SPARQL data endpoint to validate"
    :parse-fn parse-endpoint]])

(defn- usage [summary]
  (println "Usage:")
  (println summary))

(defn- invalid-args [{:keys [errors summary]}]
  (binding [*out* *err*]
    (doseq [error errors]
      (println error))
    (println)
    (usage summary)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [{:keys [errors] :as result} (cli/parse-opts args cli-options)]
    (if (nil? errors)
      (do
        (println "RUNNING...")
        (System/exit 0))
      (do (invalid-args result)
          (System/exit 1)))))
