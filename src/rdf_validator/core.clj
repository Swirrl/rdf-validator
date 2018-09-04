(ns rdf-validator.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :as cli]))

(defn parse-endpoint [endpoint-str]
  endpoint-str)

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
        (println "RUNNING..."))
      (do (invalid-args result)
          (System/exit 1)))))
