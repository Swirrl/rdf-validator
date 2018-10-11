(ns rdf-validator.test-cases
  (:require [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.stuartsierra.dependency :as dep]
            [rdf-validator.util :as util])
  (:import [java.io File]))

(defn- load-source-test [suite-name source]
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

(defmulti load-file-test-suite infer-source-test-type)

(defmethod load-file-test-suite :sparql [f]
  {:user {:tests [{:type :sparql
                   :source f
                   :name (infer-source-test-name f)
                   :suite :user}]}})

(defn- resolve-test-resource
  "Resolves a test resource by name. Throws an exception if the resource does not exist."
  [resource-name]
  (if-let [resource (io/resource resource-name)]
    resource
    (throw (ex-info (format "Invalid test resource: %s" resource-name) {:resource-name resource-name}))))

(defn- read-edn-suite
  "Reads a raw test suite definition from an EDN source."
  [f]
  (edn/read-string {:readers {'resource resolve-test-resource}} (slurp f)))

(defn- resolve-test-source
  "Resolves a test definition into an absolute data source."
  [^File suite-file test-source]
  (cond
    (string? test-source)
    (let [suite-dir (.getParentFile suite-file)]
      (io/file suite-dir test-source))

    (util/url? test-source)
    test-source

    :else
    (throw (ex-info "Test source must be either a string or resource" {:source test-source}))))

(defn- normalise-test
  "Normalises a test definition into a map."
  [test suite-file suite-name]
  (cond
    (string? test)
    (recur {:source test} suite-file suite-name)

    (util/url? test)
    (load-source-test suite-name test)

    (map? test)
    (let [test-source (resolve-test-source suite-file (:source test))]
      {:type (or (:type test) (infer-source-test-type test-source))
       :source test-source
       :name (or (:name test) (infer-source-test-name test-source))
       :suite suite-name})

    :else
    (throw (ex-info "Test definition must be either a string or a map" {:suite suite-name}))))

(defn- normalise-suite
  "Normalises a raw test suite definition."
  [source-file suite-name suite]
  (cond
    (vector? suite) (recur source-file suite-name {:tests suite})
    (map? suite) (update suite :tests #(mapv (fn [t] (normalise-test t source-file suite-name)) %))
    :else (throw (ex-info "Suite definition must be a vector or a map" {:suite suite}))))

(defmethod load-file-test-suite :edn [f]
  (let [raw (read-edn-suite f)]
    (if (map? raw)
      (into {} (map (fn [[suite-name suite]]
                      [suite-name (normalise-suite f suite-name suite)])
                    raw))
      (throw (ex-info "Root of suite document must be a map" {:file f})))))

(defmethod load-file-test-suite :default [f]
  nil)

(defn- load-directory-test-suite
  "Recursively loads all tests contained within the given directory."
  [^File dir]
  (let [suite (-> (.getName dir)
                  (string/lower-case)
                  (keyword))
        search (fn search [^File f]
                 (if (.isDirectory f)
                   (mapcat search (.listFiles f))
                   [(load-source-test suite f)]))
        tests (vec (remove nil? (search dir)))]
    {suite {:tests tests}}))

(defn load-test-suite
  "Loads a test suite definition map from a given file. If a file is passed it is considered to contain
   a single test of a type inferred from the file extension. If a directory is given the suite is constructed
   from the suites represented by each contained file. If a test type cannot be inferred for a contained file
   it is ignored and not included in the resulting suite."
  [^File file-or-dir]
  (if (.isDirectory file-or-dir)
    (load-directory-test-suite file-or-dir)
    (load-file-test-suite file-or-dir)))

(defn- merge-raw-suite
  "Merges two raw test suite definition maps."
  [{tests1 :tests extend1 :extend exclude1 :exclude} {tests2 :tests extend2 :extend exclude2 :exclude}]
  {:tests (vec (concat tests1 tests2))
   :extend (set (concat extend1 extend2))
   :exclude (set (concat exclude1 exclude2))})

(defn merge-raw-test-suites
  "Merges two raw test suite maps into a single map. Suites with the same name are merged into a
   single definition within the result map."
  [s1 s2]
  (merge-with merge-raw-suite s1 s2))

(defn- build-dependency-graph [raw-suite]
  (let [suite-names (set (keys raw-suite))
        deps (mapcat (fn [[suite-name {:keys [extend] :as suite}]]
                       (map (fn [s] [suite-name s]) extend))
                     raw-suite)]
    (reduce (fn [g [suite dep]]
              (if (contains? suite-names dep)
                (dep/depend g suite dep)
                (throw (ex-info (format "Unknown suite %s extended by suite %s" (name dep) (name suite))
                                {:suite suite :dependency dep}))))
            (dep/graph)
            deps)))

(defn- get-resolution-order
  "Returns an ordered list of test suite names specifying the order in which their tests should be resolved. Suites
   should be resolved after any test suites they extend. An exception will be thrown if any suites extend each other
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
   already have been resolved and included within resolved-suites. Imports all the tests from any extended
   suites and removes any tests specified by the :exclude key."
  [{:keys [tests extend exclude] :as raw-suite} resolved-suites]
  (let [excluded-set (set exclude)                          ;;TODO: is this required?
        included (mapcat (fn [included-suite-name]
                           (let [included-tests (get-in resolved-suites [included-suite-name :tests])]
                             (remove (fn [test] (contains? excluded-set (test-key test))) included-tests)))
                         extend)]
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
  [test-suite-files]
  (let [raw (reduce (fn [acc suite-file]
                      (let [suite (load-test-suite suite-file)]
                        (merge-raw-test-suites acc suite)))
                    nil
                    test-suite-files)]
    (resolve-imports raw)))

(defn suite-tests [suite]
  (mapcat :tests (vals suite)))
