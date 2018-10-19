(ns rdf-validator.test-cases-test
  (:require [clojure.test :refer :all]
            [rdf-validator.test-cases :refer :all]
            [clojure.java.io :as io]))

(deftest load-test-suite-query-file
  (let [f (io/file "test/suites/select.sparql")
        suite (load-test-suite f)]
    (is (= {:user {:tests [{:type :sparql
                            :source f
                            :suite :user
                            :name "select"}]}}
           suite))))

(deftest load-test-suite-query-directory
  (let [d (io/file "test/suites/queries")
        suite (load-test-suite d)]
    (is (= {:queries {:tests [{:type :sparql
                               :source (io/file d "ask.sparql")
                               :suite :queries
                               :name "ask"}
                              {:type :sparql
                               :source (io/file d "select.sparql")
                               :suite :queries
                               :name "select"}]}}
           suite))))

(deftest load-test-suite-simple
  (let [f (io/file "test/suites/simple.edn")
        suite (load-test-suite f)]
    (is (= {:simple {:tests [{:type :sparql
                              :source (io/file "test/suites/queries/ask.sparql")
                              :suite :simple
                              :name "ask"}
                             {:type :sparql
                              :source (io/file "test/suites/queries/select.sparql")
                              :suite :simple
                              :name "select"}]}}
           suite))))

(deftest load-test-suite-with-resources
  (let [f (io/file "test/suites/with-resources.edn")
        suite (load-test-suite f)]
    (is (= {:resources {:tests [{:type :sparql
                                 :source (io/resource "ask_resource.sparql")
                                 :suite :resources
                                 :name "ask_resource"}
                                {:type :sparql
                                 :source (io/resource "select_resource.sparql")
                                 :suite :resources
                                 :name "select_resource"}]}}
           suite))))

(deftest load-test-suite-explicit
  (let [f (io/file "test/suites/explicit-tests.edn")
        suite (load-test-suite f)]
    (is (= {:explicit {:tests [{:type :sparql
                                :source (io/file "test/suites/queries/ask.sparql")
                                :name "ask"
                                :suite :explicit}
                               {:type :other
                                :source (io/file "test/suites/queries/select.sparql")
                                :name "select"
                                :suite :explicit}
                               {:type :sparql
                                :source (io/resource "ask_resource.sparql")
                                :name "embedded"
                                :suite :explicit}]}}
           suite))))

(deftest merge-raw-test-suites-test
  (testing "Disjoint"
    (let [s1 {:suite1 {:tests [{:type :sparql
                                :source (io/file "test/suites/queries/ask.sparql")
                                :suite :suite1
                                :name "ask"}]}}
          s2 {:suite2 {:tests [{:type :sparql
                                :source (io/file "test/suites/queries/select.sparql")
                                :suite :suite2
                                :name "select"}]}}]
      (is (= (merge s1 s2) (merge-raw-test-suites s1 s2)))))

  (testing "Intersecting"
    (let [t1 {:type :sparql
              :source (io/file "test/suites/queries/ask.sparql")
              :suite :suite
              :name "ask"}
          t2 {:type :sparql
              :source (io/file "test/suites/queries/select.sparql")
              :suite :suite
              :name "select"}
          t3 {:type :sparql
              :source (io/resource "select_resource.sparql")
              :suite :other
              :name "select_resource"}
          s1 {:suite {:tests [t1]}}
          s2 {:suite {:tests [t2]}
              :other {:tests [t3]}}]
      (is (= {:suite {:tests [t1 t2]
                      :exclude #{}
                      :import #{}}
              :other {:tests [t3]}}
             (merge-raw-test-suites s1 s2))))))

(deftest resolve-extensions-test
  (testing "valid"
    (let [t1 {:type   :sparql
              :source (io/file "test/suites/queries/ask.sparql")
              :suite  :suite1
              :name   "ask"}
          t2 {:type   :sparql
              :source (io/file "test/suites/queries/select.sparql")
              :suite  :suite2
              :name   "select"}
          t3 {:type   :sparql
              :source (io/resource "ask_resource.sparql")
              :suite  :suite2
              :name   "ask_resource"}
          t4 {:type   :sparql
              :source (io/resource "select_resource.sparql")
              :suite  :suite4
              :name   "select_resource"}
          suites {:suite1 {:tests [t1]}
                  :suite2 {:tests [t2 t3]}
                  :suite3 {:import  [:suite1 :suite2]
                           :exclude [:suite2/ask_resource]}
                  :suite4 {:tests  [t4]
                           :import [:suite1]}}
          expected {:suite1 {:tests [t1]}
                    :suite2 {:tests [t2 t3]}
                    :suite3 {:tests [t1 t2]}
                    :suite4 {:tests [t1 t4]}}]
      (is (= expected (resolve-imports suites)))))

  (testing "circular depedency"
    (let [suites {:suite1 {:import [:suite2]}
                  :suite2 {:import [:suite1]}}]
      (is (thrown? Exception (resolve-imports suites)))))

  (testing "unknown extension"
    (let [suites {:suite {:import [:unknown]
                          :tests [{:type :sparql
                                   :source (io/file "test/suites/queries/ask.sparql")
                                   :suite :suite
                                   :name "ask"}]}}]
      (is (thrown? Exception (resolve-imports suites))))))

(deftest resolve-test-suites-imports
  (let [suite-files [(io/file "test/suites/simple.edn")
                     (io/file "test/suites/imports.edn")]
        expected {:simple {:tests [{:type :sparql
                                    :source (io/file "test/suites/queries/ask.sparql")
                                    :suite :simple
                                    :name "ask"}
                                   {:type :sparql
                                    :source (io/file "test/suites/queries/select.sparql")
                                    :suite :simple
                                    :name "select"}]}
                  :imports {:tests [{:type :sparql
                                     :source (io/file "test/suites/queries/select.sparql")
                                     :suite :simple
                                     :name "select"}
                                    {:type :sparql
                                     :source (io/resource "ask_resource.sparql")
                                     :suite :imports
                                     :name "ask_resource"}]}}]
    (is (= expected (resolve-test-suites suite-files)))))

(deftest suite-tests-test
  (let [test1 {:name "test1"}
        test2 {:name "test2"}
        test3 {:name "test3"}
        test4 {:name "test4"}
        suites {:suite1 {:tests [test1 test2]}
                :suite2 {:tests [test3]}
                :suite3 {:tests [test4]}}]
    (testing "specified suites"
      (let [to-run-names ["suite1" "suite3" "missing"]
            to-run (suite-tests suites to-run-names)]
        (is (= #{test1 test2 test4} (set to-run)))))

    (testing "all suites"
      (is (= #{test1 test2 test3 test4}
             (set (suite-tests suites [])))))))
