(ns rdf-validator.reporting
  "Used for creating reports of test executions."
  (:require [clojure.string :as string])
  (:import [java.io File]))

(defprotocol TestReporter
  (report-test-result! [this test-result]
    "Reports the outcome of a single test execution")
  (report-test-summary! [this summary]
    "Reports a summary of all test executions"))

(defrecord ConsoleTestReporter []
  TestReporter
  (report-test-result! [_this {:keys [number ^File source-file result errors] :as test-result}]
    (println (format "%d %s: %s" number (.getAbsolutePath source-file) (string/upper-case (name result))))
    (doseq [error errors]
      (println (format "\t%s" error)))
    (when (pos? (count errors))
      (println)))

  (report-test-summary! [_this {:keys [passed failed errored ignored] :as test-summary}]
    (println)
    (println (format "Passed %d Failed %d Errored %d Ignored %d" passed failed errored ignored))))

