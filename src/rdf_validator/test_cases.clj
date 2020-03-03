(ns rdf-validator.test-cases
  (:require [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.stuartsierra.dependency :as dep]
            [rdf-validator.util :as util])
  (:import [java.io File]
           [java.net URL URI]))

(defprotocol Relatable
  (resolve-relative [this ^String relative]
    "Resolves the location of an item relative to this"))

(extend-protocol Relatable
  File
  (resolve-relative [^File f relative]
    (if (.isDirectory f)
      (io/file f relative)
      (io/file (.getParentFile f) relative)))

  URI
  (resolve-relative [^URI uri ^String relative]
    (.resolve uri relative))

  URL
  (resolve-relative [^URL url ^String relative]
    (let [^URI uri (resolve-relative (.toURI url) relative)]
      (.toURL uri))))

(defn- load-source-test
  "Loads a test definition from the specified data source. Infers the test type and name from the name of
   the source."
  [suite-name source]
  (let [[n ext] (util/split-file-name-extension (util/get-file-name source))
        type (some-> ext keyword)]
    (if (= :sparql type)
      {:type type
       :source source
       :name n
       :suite suite-name})))

(defn- infer-source-test-type
  "Returns a keyword representing the inferred test type for a given test source, or nil if
   no type could be inferred."
  [source]
  (if-let [ext (util/get-file-extension source)]
    (keyword ext)))

(defn- infer-source-test-name
  "Derives the name of a test from the source."
  [source]
  (-> source (util/get-file-name) (util/split-file-name-extension) (first)))

(defmulti load-source-test-suite infer-source-test-type)

(defmethod load-source-test-suite :sparql [source]
  {:user {:tests [{:type :sparql
                   :source source
                   :name (infer-source-test-name source)
                   :suite :user}]}})

(defn- read-edn-suite
  "Reads a raw test suite definition from an EDN source."
  [source]
  (edn/read-string (slurp source)))

(defn- load-directory-tests
  "Loads all tests defined within the specified directory."
  [suite-name ^File dir]
  (let [search (fn search [^File f]
                 (if (.isDirectory f)
                   (mapcat search (.listFiles f))
                   [(load-source-test suite-name f)]))]
    (vec (remove nil? (search dir)))))

(defn- load-directory-test-suite
  "Recursively loads all tests contained within the given directory."
  [^File dir]
  (let [suite-name (-> (.getName dir)
                       (string/lower-case)
                       (keyword))
        tests (load-directory-tests suite-name dir)]
    {suite-name {:tests tests}}))

(defprotocol TestCasesSource
  (load-tests [this suite-name]))

(extend-protocol TestCasesSource
  File
  (load-tests [^File f suite-name]
    (if (.isDirectory f)
      (load-directory-tests suite-name f)
      (if-let [t (load-source-test suite-name f)]
        [t])))

  URL
  (load-tests [^URL url suite-name]
    (if-let [t (load-source-test suite-name url)]
      [t])))

(defn- normalise-tests
  "Loads normalised test definitions from a test source referenced within an EDN test suite file.
   The source may resolve to multiple contained test definitions."
  [suite-source suite-name test]
  (cond
    (string? test)
    (let [test-source (resolve-relative suite-source test)]
      (vec (load-tests test-source suite-name)))

    ;;TODO: allow a test map to describe a collection of tests?
    (map? test)
    (let [test-source (resolve-relative suite-source (:source test))]
      [{:type   (or (:type test) (infer-source-test-type test-source))
        :source test-source
        :name   (or (:name test) (infer-source-test-name test-source))
        :suite  suite-name}])

    :else
    (throw (ex-info "Test definition must be either a string or a map" {:suite suite-name}))))

(defn- normalise-suite
  "Normalises a raw test suite definition."
  [source suite-name suite]
  (cond
    (vector? suite) (recur source suite-name {:tests suite})
    (map? suite) (update suite :tests #(vec (mapcat (fn [t] (normalise-tests source suite-name t)) %)))
    :else (throw (ex-info "Suite definition must be a vector or a map" {:suite suite}))))

(defmethod load-source-test-suite :edn [source]
  (let [raw (read-edn-suite source)]
    (if (map? raw)
      (into {} (map (fn [[suite-name suite]]
                      [suite-name (normalise-suite source suite-name suite)])
                    raw))
      (throw (ex-info "Root of test suite document must be a map" {:source source})))))

(defmethod load-source-test-suite :default [f]
  nil)

(defprotocol TestSuiteSource
  (load-test-suite [this]
    "Loads a test suite definition map from a given file. If a file is passed it is considered to contain
     a single test of a type inferred from the file extension. If a directory is given the suite is constructed
     from the suites represented by each contained file. If a test type cannot be inferred for a contained file
     it is ignored and not included in the resulting suite."))

(extend-protocol TestSuiteSource
  File
  (load-test-suite [file-or-dir]
    (if (.isDirectory file-or-dir)
      (load-directory-test-suite file-or-dir)
      (load-source-test-suite file-or-dir)))

  URL
  (load-test-suite [url]
    (load-source-test-suite url)))

(defn- merge-raw-suite
  "Merges two raw test suite definition maps."
  [{tests1 :tests import1 :import exclude1 :exclude} {tests2 :tests import2 :import exclude2 :exclude}]
  {:tests (vec (concat tests1 tests2))
   :import (set (concat import1 import2))
   :exclude (set (concat exclude1 exclude2))})

(defn merge-raw-test-suites
  "Merges two raw test suite maps into a single map. Suites with the same name are merged into a
   single definition within the result map."
  [s1 s2]
  (merge-with merge-raw-suite s1 s2))

(defn- build-dependency-graph [raw-suite]
  (let [suite-names (set (keys raw-suite))
        deps (mapcat (fn [[suite-name {:keys [import] :as suite}]]
                       (map (fn [s] [suite-name s]) import))
                     raw-suite)]
    (reduce (fn [g [suite dep]]
              (if (contains? suite-names dep)
                (dep/depend g suite dep)
                (throw (ex-info (format "Unknown suite %s imported by suite %s" (name dep) (name suite))
                                {:suite suite :dependency dep}))))
            (dep/graph)
            deps)))

(defn- get-resolution-order
  "Returns an ordered list of test suite names specifying the order in which their tests should be resolved. Suites
   should be resolved after any test suites they import. An exception will be thrown if any suites import each other
   cyclically."
  [raw-suites]
  (let [dep-graph (build-dependency-graph raw-suites)
        dep-suites (dep/nodes dep-graph)
        independent-suites (remove (fn [s] (contains? dep-suites s)) (keys raw-suites))]
    (concat independent-suites (dep/topo-sort dep-graph))))

(defn- test-key [{suite :suite test-name :name}]
  (keyword (name suite) test-name))

(defn- resolve-suite
  "Resolves the tests to be included within a test suite. Any suites imported by the current suite should
   already have been resolved and included within resolved-suites. Imports all the tests from any imported
   suites and removes any tests specified by the :exclude key."
  [{:keys [tests import exclude] :as raw-suite} resolved-suites]
  (let [excluded-set (set exclude)                          ;;TODO: is this required?
        included (mapcat (fn [included-suite-name]
                           (let [included-tests (get-in resolved-suites [included-suite-name :tests])]
                             (remove (fn [test] (contains? excluded-set (test-key test))) included-tests)))
                         import)]
    {:tests (vec (concat included tests))}))

(defn- resolve-suite-imports
  "Resolves imports for a map of test suite definitions in the order specified by resolution-order. An exception is
   thrown if any referenced test suite is not defined."
  [raw-suites resolution-order]
  (reduce (fn [acc suite-name]
            (let [raw-suite (get raw-suites suite-name ::missing)]
              (if (= ::missing raw-suite)
                (throw (ex-info (format "Cannot resolve test suite %s" (name suite-name)) {:suites raw-suites :suite suite-name}))
                (let [resolved (resolve-suite raw-suite acc)]
                  (assoc acc suite-name resolved)))))
          {}
          resolution-order))

(defn resolve-imports
  "Resolves test suite extensions within a map of test suite definitions. Each referenced suite must exist
   within raw-suite."
  [raw-suite]
  (let [resolution-order (get-resolution-order raw-suite)]
    (resolve-suite-imports raw-suite resolution-order)))

(defn resolve-test-suites
  "Loads a sequence of test suite files, resolves any declared imports and merges the resulting suite maps
   into a single suite map."
  [test-suite-sources]
  (let [raw (reduce (fn [acc suite-source]
                      (let [suite (load-test-suite suite-source)]
                        (merge-raw-test-suites acc suite)))
                    nil
                    test-suite-sources)]
    (resolve-imports raw)))

(defn classpath-test-suite-sources []
  (util/resources "rdf-validator-suite.edn"))

(defn suite-tests
  "Given a test suite map and a collection of test suite names to run, returns a sequence of test cases to be executed.
   If no suite names are specified, all tests for all suites are returned."
  [suites to-run-names]
  (let [get-suite (fn [suite-name]
                    (get suites (keyword suite-name)))
        suites-to-run (if (seq to-run-names)
                        (map get-suite to-run-names)
                        (vals suites))]
    (mapcat :tests suites-to-run)))
