(ns rdf-validator.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [grafter-2.rdf4j.repository :as repo]
            [rdf-validator.endpoint :as endpoint]
            [rdf-validator.query :as query]
            [rdf-validator.reporting :as reporting]
            [rdf-validator.test-cases :as tc]
            [selmer.parser :as selmer]
            [selmer.util :refer [without-escaping set-missing-value-formatter!]]
            [clojure.edn :as edn])
  (:import [java.net URI]
           [org.apache.jena.query QueryFactory Syntax]
           [java.io File]
           [clojure.lang ExceptionInfo]))

(defn- conj-in [m k v]
  (update-in m [k] conj v))

(defn parse-variables-file [f]
  (edn/read-string (slurp f)))

(defn- usage [summary]
  (println "Usage:")
  (println summary))

(defn- report-invalid-args [{:keys [errors summary]}]
  (binding [*out* *err*]
    (doseq [error errors]
      (println error))
    (println)
    (usage summary)))

(defn- report-exception [^Exception ex]
  (binding [*out* *err*]
    (println (.getMessage ex))
    (.printStackTrace ex)
    (System/exit 1)))

(defn on-missing-query-template-variable [{:keys [tag-value] :as tag} context]
  (throw (ex-info (format "No variable specified for template variable '%s'" tag-value) {})))

(defn load-sparql-template [f variables]
  (let [query-string (slurp f)]
    (without-escaping (selmer/render query-string variables))))

(defn find-test-files [suites]
  (mapcat (fn [^File f]
            (if (.isDirectory f)
              (find-test-files (.listFiles f))
              [f]))
          suites))

(defn run-sparql-ask-test 
  [{:keys [test-source query-string]} {:keys [repository dataset] :as endpoint}]
  (with-open [conn (repo/->connection repository)]
    (let [pquery (repo/prepare-query conn query-string dataset)
          failed (query/execute pquery)]
      {:test-source test-source
       :result      (if failed :failed :passed)
       :errors      (if failed ["ASK query returned true"] [])})))

(defn run-sparql-select-test 
  [{:keys [test-source query-string]} {:keys [repository dataset] :as endpoint}]
  (with-open [conn (repo/->connection repository)] 
    (let [pquery (repo/prepare-query conn query-string dataset)
          results (query/execute pquery)
          failed (pos? (count results))]
      {:test-source test-source
       :result      (if failed :failed :passed)
       :errors      (mapv str results)})))

(defn run-test-case [{:keys [source] :as test-case} query-variables endpoint]
  (try
    (let [^String sparql-str (load-sparql-template source query-variables)
          query (QueryFactory/create sparql-str Syntax/syntaxSPARQL_11)
          test {:test-source source :query-string sparql-str}]
      (cond
        (.isAskType query) (run-sparql-ask-test test endpoint)
        (.isSelectType query) (run-sparql-select-test test endpoint)
        :else {:test-source source
               :result :ignored
               :errors []}))
    (catch Exception ex
      {:test-source source
       :result :errored
       :errors [(.getMessage ex)]})))

(defn run-test-cases [test-cases query-variables endpoint reporter]
  (let [summary (reduce (fn [summary [test-index test-case]]
                          (let [{:keys [result] :as test-result} (run-test-case test-case query-variables endpoint)]
                            (reporting/report-test-result! reporter (assoc test-result :number (inc test-index)))
                            (update summary result inc)))
                        {:failed 0 :passed 0 :errored 0 :ignored 0}
                        (map-indexed vector test-cases))]
    (reporting/report-test-summary! reporter summary)
    summary))

(defn- create-endpoint [{:keys [endpoint graph] :as options}]
  (endpoint/create-endpoint endpoint graph))

(def cli-options
  [["-s" "--suite SUITE" "Test suite file or directory"
    :default []
    :parse-fn io/file
    :validate [(fn [f]
                 ;;TODO: check file exists
                 true) "File does not exist"]
    :assoc-fn conj-in]
   ["-e" "--endpoint ENDPOINT" "SPARQL data endpoint to validate"
    :parse-fn endpoint/parse-repository]
   ["-g" "--graph GRAPH" "Graph to include in the RDF dataset"
    :default []
    :parse-fn #(URI. %)
    :assoc-fn conj-in]
   ["-v" "--variables FILE" "EDN file containing query variables"
    :parse-fn parse-variables-file
    :default {}]])

(defn inner-main 
  [args]
  (let [{:keys [errors options] :as result} (cli/parse-opts args cli-options)]
    (if (nil? errors)
      (let [suite-sources (concat (:suite options) (tc/classpath-test-suite-sources))
            endpoint (create-endpoint options)
            query-variables (:variables options)
            suites (tc/resolve-test-suites suite-sources)
            suites-to-run (:arguments result)
            test-cases (tc/suite-tests suites suites-to-run)
            test-reporter (reporting/->ConsoleTestReporter)]
        (run-test-cases test-cases query-variables endpoint test-reporter))
      (throw (ex-info "Invalid command line arguments" {:type :invalid-cli-arguments
                                                        :cli-result result})))))

(defn -main
  [& args]
  (try
    (set-missing-value-formatter! on-missing-query-template-variable)
    (let [{:keys [failed errored] :as test-summary} (inner-main args)]
      (System/exit (+ failed errored)))
    (catch ExceptionInfo exi 
      (let [exi-data (ex-data exi)]
        (if (= :invalid-cli-arguments (:type exi-data))
          (report-invalid-args (:cli-result exi-data))
          (report-exception exi))
        (System/exit 1)))
    (catch Exception ex
      (report-exception ex)
      (System/exit 1))))
