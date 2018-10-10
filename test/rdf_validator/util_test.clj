(ns rdf-validator.util-test
  (:require [clojure.test :refer :all]
            [rdf-validator.util :refer :all]))

(deftest split-file-name-extension-test
  (are [file-name expected] (= expected (split-file-name-extension file-name))
    "file.txt" ["file" "txt"]
    "file.tar.gz" ["file.tar" "gz"]
    "file" ["file" nil]
    "file." ["file" ""]))
