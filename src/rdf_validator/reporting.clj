(ns rdf-validator.reporting
  "Used for creating reports of test executions."
  (:require [clojure.string :as string]
            [rdf-validator.util :as util]
            [clojure.pprint :as pp]))

(defprotocol TestReporter
  (report-test-result! [this test-result]
    "Reports the outcome of a single test execution")
  (report-test-summary! [this summary]
    "Reports a summary of all test executions"))

(defrecord ConsoleTestReporter []
  TestReporter
  (report-test-result! [_this {:keys [number test-source result errors] :as test-result}]
    (println (format "%d %s: %s" number (util/get-path test-source) (string/upper-case (name result))))
    (when (seq errors)
      (if (map? (first errors))
        (pp/print-table errors)
        (doseq [error errors]
          (println (format "\t%s" error)))))
    (when (pos? (count errors))
      (println)))

  (report-test-summary! [_this {:keys [passed failed errored ignored] :as test-summary}]
    (println)
    (println (format "Passed %d Failed %d Errored %d Ignored %d" passed failed errored ignored))))
