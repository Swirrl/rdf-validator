(ns rdf-validator.query
  (:require [grafter-2.rdf4j.repository :as repo])
  (:import (org.eclipse.rdf4j.query BooleanQuery TupleQuery Update GraphQuery)))

(defn get-query-type
  "Returns a keyword indicating the type query represented by the
  given prepared query. Returns nil if the query could not be
  classified."
  [pquery]
  (condp instance? pquery
    TupleQuery :select
    BooleanQuery :ask
    GraphQuery :construct
    Update :update
    nil))

(defmulti execute (fn [query] (class query)))

(defmethod execute TupleQuery [tuple-query]
  (vec (repo/evaluate tuple-query)))

(defmethod execute BooleanQuery [ask-query]
  (repo/evaluate ask-query))

(defmethod execute GraphQuery [graph-query]
  (vec (repo/evaluate graph-query)))
