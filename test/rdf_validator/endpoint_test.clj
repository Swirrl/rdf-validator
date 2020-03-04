(ns rdf-validator.endpoint-test
  (:require [clojure.test :refer :all]
            [rdf-validator.endpoint :refer :all]
            [grafter-2.rdf4j.repository :as repo]
            [grafter-2.rdf4j.io :as rdf]
            [clojure.java.io :as io])
  (:import [org.eclipse.rdf4j.repository.sparql SPARQLRepository]))

(deftest parse-repository-sparql-repo-test
  (let [uri-str "http://sparql-endpoint"
        repo (parse-repository uri-str)]
    (is (instance? SPARQLRepository repo) "Not instance of SPARQLRepository")))

(deftest parse-repository-file-uri-test
  (let [rel-file (io/file "test/data/example.ttl")
        file-uri (str "file://" (.getAbsolutePath rel-file))
        repo (parse-repository file-uri)]
    (is (= 2 (count (with-open [conn (repo/->connection repo)]
                      (rdf/statements conn)))))))

(deftest parse-repository-directory-test
  (let [dir-str "test/data/test-dir"
        repo (parse-repository dir-str)]
    (is (= 2 (count (with-open [conn (repo/->connection repo)]
                      (rdf/statements conn)))))))

(deftest parse-repository-file-name-test
  (let [file-str "test/data/example.ttl"
        repo (parse-repository file-str)]
    (is (= 2 (count (with-open [conn (repo/->connection repo)]
                      (rdf/statements conn)))))))
