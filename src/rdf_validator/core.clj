(ns rdf-validator.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [grafter.rdf :as rdf]
            [grafter.rdf.repository :as repo]
            [clojure.string :as string])
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
    {:source-file source-file
     :result (if failed :failed :passed)
     :errors (if failed ["ASK query returned true"] [])}))

(defmethod run-test-case :sparql-select [{:keys [query-string source-file] :as test-case} repository]
  (let [results (vec (repo/query repository query-string))
        failed (pos? (count results))]
    {:source-file source-file
     :result (if failed :failed :passed)
     :errors (mapv str results)}))

(defmethod run-test-case :sparql-ignored [{:keys [source-file]} _repository]
  {:source-file source-file
   :result :ignored
   :errors []})

(defn display-test-result [{:keys [number source-file result errors] :as test-result}]
  (println (format "%d %s: %s" number (.getAbsolutePath source-file) (string/upper-case (name result))))
  (doseq [error errors]
    (println (format "\t%s" error)))
  (when (pos? (count errors))
    (println)))

(defn run-test-cases [test-cases repository]
  (reduce (fn [summary [test-index test-case]]
            (let [{:keys [result] :as test-result} (run-test-case test-case repository)]
              (display-test-result (assoc test-result :number (inc test-index)))
              (update summary result inc)))
          {:failed 0 :passed 0 :ignored 0}
          (map-indexed vector test-cases)))

(defn -main
  [& args]
  (let [{:keys [errors options] :as result} (cli/parse-opts args cli-options)]
    (if (nil? errors)
      (let [suites (:suite options)
            repository (:endpoint options)
            test-cases (mapcat load-test-cases suites)
            {:keys [passed failed ignored]} (run-test-cases test-cases repository)]
        (println)
        (println (format "Passed %d Failed %d Ignored %d" passed failed ignored))
        (System/exit failed))
      (do (invalid-args result)
          (System/exit 1)))))
