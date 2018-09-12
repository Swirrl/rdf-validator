(ns rdf-validator.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [grafter.rdf :as rdf]
            [grafter.rdf.repository :as repo]
            [clojure.string :as string]
            [selmer.parser :as selmer]
            [selmer.util :refer [without-escaping set-missing-value-formatter!]]
            [clojure.edn :as edn])
  (:import [java.net URI URISyntaxException]
           [org.apache.jena.query QueryFactory Syntax]
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

(defn parse-endpoint [endpoint-str]
  (try
    (uri->repository (URI. endpoint-str))
    (catch URISyntaxException ex
      (file->repository (io/file endpoint-str)))))

(defn parse-variables-file [f]
  (edn/read-string (slurp f)))

(def cli-options
  [["-s" "--suite SUITE" "Test suite file or directory"
    :default []
    :parse-fn io/file
    :validate [(fn [f]
                 ;;TODO: check file exists
                 true) "File does not exist"]
    :assoc-fn (fn [m k v] (update-in m [k] conj v))]
   ["-e" "--endpoint ENDPOINT" "SPARQL data endpoint to validate"
    :parse-fn parse-endpoint]
   ["-v" "--variables FILE" "EDN file containing query variables"
    :parse-fn parse-variables-file
    :default {}]])

(defn- usage [summary]
  (println "Usage:")
  (println summary))

(defn- invalid-args [{:keys [errors summary]}]
  (binding [*out* *err*]
    (doseq [error errors]
      (println error))
    (println)
    (usage summary)))

(defn on-missing-query-template-variable [{:keys [tag-value] :as tag} context]
  (throw (ex-info (format "No variable specified for template variable '%s'" tag-value) {})))

(set-missing-value-formatter! on-missing-query-template-variable)

(defn load-sparql-template [f variables]
  (let [query-string (slurp f)]
    (without-escaping (selmer/render query-string variables))))

(defn load-test-case [^File f query-variables]
  (try
    (let [^String sparql-str (load-sparql-template f query-variables)
          query (QueryFactory/create sparql-str Syntax/syntaxSPARQL_11)
          type (cond
                 (.isAskType query) :sparql-ask
                 (.isSelectType query) :sparql-select
                 :else :sparql-ignored)]
      {:source-file  f
       :type         type
       :query-string sparql-str})
    (catch Exception ex
      {:source-file f
       :type :invalid
       :exception ex})))

(defn load-test-cases [^File f query-variables]
  (if (.isDirectory f)
    (mapcat (fn [cf] (load-test-cases cf query-variables)) (.listFiles f))
    [(load-test-case f query-variables)]))

(defmulti run-test-case (fn [test-case repository] (:type test-case)))

(defmethod run-test-case :sparql-ask [{:keys [query-string source-file] :as test-case} repository]
  (try
    (let [failed (repo/query repository query-string)]
      {:source-file source-file
       :result      (if failed :failed :passed)
       :errors      (if failed ["ASK query returned true"] [])})
    (catch Exception ex
      {:source-file source-file
       :result :errored
       :errors [(.getMessage ex)]})))

(defmethod run-test-case :sparql-select [{:keys [query-string source-file] :as test-case} repository]
  (try
    (let [results (vec (repo/query repository query-string))
          failed (pos? (count results))]
      {:source-file source-file
       :result      (if failed :failed :passed)
       :errors      (mapv str results)})
    (catch Exception ex
      {:source-file source-file
       :result :errored
       :errors [(.getMessage ex)]})))

(defmethod run-test-case :sparql-ignored [{:keys [source-file]} _repository]
  {:source-file source-file
   :result :ignored
   :errors []})

(defmethod run-test-case :invalid [{:keys [source-file ^Throwable exception]} _repository]
  {:source-file source-file
   :result :errored
   :errors [(.getMessage exception)]})

(defn display-test-result [{:keys [number ^File source-file result errors] :as test-result}]
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
          {:failed 0 :passed 0 :errored 0 :ignored 0}
          (map-indexed vector test-cases)))

(defn -main
  [& args]
  (let [{:keys [errors options] :as result} (cli/parse-opts args cli-options)]
    (if (nil? errors)
      (let [suites (:suite options)
            repository (:endpoint options)
            query-variables (:variables options)
            test-cases (mapcat (fn [f] (load-test-cases f query-variables)) suites)
            {:keys [passed failed errored ignored]} (run-test-cases test-cases repository)]
        (println)
        (println (format "Passed %d Failed %d Errored %d Ignored %d" passed failed errored ignored))
        (System/exit (+ failed errored)))
      (do (invalid-args result)
          (System/exit 1)))))

