(ns rdf-validator.core-test
  (:require [clojure.test :refer :all]
            [rdf-validator.core :refer :all]
            [grafter.rdf :as rdf]
            [clojure.java.io :as io])
  (:import [org.openrdf.repository.sparql SPARQLRepository]))

(deftest parse-endpoint-sparql-repo-test
  (let [uri-str "http://sparql-endpoint"
        repo (parse-endpoint uri-str)]
    (is (instance? SPARQLRepository repo) "Not instance of SPARQLRepository")))

(deftest parse-endpoint-file-uri-test
  (let [rel-file (io/file "test/data/example.ttl")
        file-uri (str "file://" (.getAbsolutePath rel-file))
        repo (parse-endpoint file-uri)]
    (is (= 2 (count (rdf/statements repo))))))

(deftest parse-endpoint-directory-test
  (let [dir-str "test/data/test-dir"
        repo (parse-endpoint dir-str)]
    (is (= 2 (count (rdf/statements repo))))))

(deftest parse-endpoint-file-name-test
  (let [file-str "test/data/example.ttl"
        repo (parse-endpoint file-str)]
    (is (= 2 (count (rdf/statements repo))))))
