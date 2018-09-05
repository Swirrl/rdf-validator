(ns rdf-validator.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [grafter.rdf :as rdf]
            [grafter.rdf.repository :as repo])
  (:import [java.net URI URISyntaxException]
           [org.apache.jena.query Query QueryFactory Syntax]
           [java.io File FileFilter]))

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

(defn parse-endpoint [endpoint-str]
  (try
    (uri->repository (URI. endpoint-str))
    (catch URISyntaxException ex
      (file->repository (io/file endpoint-str)))))

(def cli-options
  [["-s" "--suite SUITE" "Test suite file or directory"
    :default []
    :parse-fn io/file
    :validate [(fn [f]
                 ;;TODO: check file exists
                 true) "File does not exist"]
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

(defn load-test-case [^File f]
  (let [^String sparql-str (slurp f)
        query (QueryFactory/create sparql-str Syntax/syntaxSPARQL_11)
        type (cond
               (.isAskType query) :sparql-ask
               (.isSelectType query) :sparql-select
               :else :sparql-ignored)]
    {:source-file f
     :type type
     :query-string sparql-str}))

(defn load-test-cases [^File f]
  (if (.isDirectory f)
    (mapcat load-test-cases (.listFiles f))
    [(load-test-case f)]))

(defmulti run-test-case (fn [test-case repository] (:type test-case)))

(defmethod run-test-case :sparql-ask [{:keys [query-string source-file] :as test-case} repository]
  (let [failed (repo/query repository query-string)]
    (println "File: " (.getAbsolutePath source-file))
    (println query-string)
    (when failed
      (println)
      (println "FAILED"))))

(defmethod run-test-case :sparql-select [{:keys [query-string source-file] :as test-case} repository]
  (let [results (vec (repo/query repository query-string))]
    (println "File: " (.getAbsolutePath source-file))
    (println query-string)

    (when (pos? (count results))
      (println)
      (println "FAILED")
      (doseq [bindings results]
        (println bindings)))))

(defmethod run-test-case :sparql-ignored [{:keys [query-string source-file]} repository]
  (println "File: " (.getAbsolutePath source-file))
  (println query-string)
  (println)
  (println "IGNORED"))

(defn -main
  [& args]
  (let [{:keys [errors options] :as result} (cli/parse-opts args cli-options)]
    (if (nil? errors)
      (let [suites (:suite options)
            repository (:endpoint options)
            test-cases (mapcat load-test-cases suites)
            case-count (count test-cases)]
        (doseq [[test-index test-case] (map-indexed vector test-cases)]
          (printf "Running test %d of %d\n\n" (inc test-index) case-count)
          (run-test-case test-case repository)
          (println))
        (System/exit 0))
      (do (invalid-args result)
          (System/exit 1)))))
