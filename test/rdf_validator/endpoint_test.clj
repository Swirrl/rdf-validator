(ns rdf-validator.endpoint-test
  (:require [clojure.test :refer :all]
            [rdf-validator.endpoint :refer :all]
            [grafter.rdf :as rdf]
            [clojure.java.io :as io])
  (:import [org.openrdf.repository.sparql SPARQLRepository]))

(deftest parse-repository-sparql-repo-test
  (let [uri-str "http://sparql-endpoint"
        repo (parse-repository uri-str)]
    (is (instance? SPARQLRepository repo) "Not instance of SPARQLRepository")))

(deftest parse-repository-file-uri-test
  (let [rel-file (io/file "test/data/example.ttl")
        file-uri (str "file://" (.getAbsolutePath rel-file))
        repo (parse-repository file-uri)]
    (is (= 2 (count (rdf/statements repo))))))

(deftest parse-repository-directory-test
  (let [dir-str "test/data/test-dir"
        repo (parse-repository dir-str)]
    (is (= 2 (count (rdf/statements repo))))))

(deftest parse-repository-file-name-test
  (let [file-str "test/data/example.ttl"
        repo (parse-repository file-str)]
    (is (= 2 (count (rdf/statements repo))))))
