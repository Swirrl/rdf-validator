(ns rdf-validator.test-cases
  (:require [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.stuartsierra.dependency :as dep]
            [rdf-validator.util :as util])
  (:import [java.io File]))

(defn- load-source-test [suite source]
  (let [[n ext] (util/split-file-name-extension (util/get-file-name source))
        type (some-> ext keyword)]
    (if (= :sparql type)
      {:type type
       :source source
       :name n
       :suite suite})))

(defn- infer-source-test-type [source]
  (if-let [ext (util/get-file-extension source)]
    (keyword ext)))

(defn- infer-source-test-name [source]
  (-> source (util/get-file-name) (util/split-file-name-extension) (first)))

(defmulti load-file-test-suite infer-source-test-type)

(defmethod load-file-test-suite :sparql [f]
  {:user {:tests [{:type :sparql
                   :source f
                   :name (infer-source-test-name f)
                   :suite :user}]}})

(defn- read-edn-suite [f]
  (edn/read-string {:readers {'resource io/resource}} (slurp f)))

(defn- resolve-test-source [^File suite-file test-source]
  (cond
    (string? test-source)
    (let [suite-dir (.getParentFile suite-file)]
      (io/file suite-dir test-source))

    (util/url? test-source)
    test-source

    :else
    (throw (ex-info "Test source must be either a string or resource" {:source test-source}))))

(defn- normalise-test [test suite-file suite-name]
  (cond
    (string? test)
    (let [test-file (resolve-test-source suite-file test)]
      (if-let [test-def (load-source-test suite-name test-file)]
        test-def
        (throw (ex-info "Invalid test definition" {:test test :suite suite-name}))))

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

(defn- normalise-suite [source-file suite-name suite]
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

(defn- load-directory-test-suite [^File dir]
  (let [suite (-> (.getName dir)
                  (string/lower-case)
                  (keyword))
        search (fn search [^File f]
                 (if (.isDirectory f)
                   (mapcat search (.listFiles f))
                   [(load-source-test suite f)]))
        tests (vec (remove nil? (search dir)))]
    {suite {:tests tests}}))

(defn load-test-suite [^File file-or-dir]
  (if (.isDirectory file-or-dir)
    (load-directory-test-suite file-or-dir)
    (load-file-test-suite file-or-dir)))

(defn- merge-suite [{tests1 :tests extend1 :extend exclude1 :exclude} {tests2 :tests extend2 :extend exclude2 :exclude}]
  {:tests (vec (concat tests1 tests2))
   :extend (set (concat extend1 extend2))
   :exclude (set (concat exclude1 exclude2))})

(defn merge-raw-test-suites [s1 s2]
  (merge-with merge-suite s1 s2))

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

(defn- get-resolution-order [raw-suites]
  (let [dep-graph (build-dependency-graph raw-suites)
        dep-suites (dep/nodes dep-graph)
        independent-suites (remove (fn [s] (contains? dep-suites s)) (keys raw-suites))]
    (concat independent-suites (dep/topo-sort dep-graph))))

(defn- test-key [{suite :suite test-name :name}]
  (keyword (name suite) test-name))

(defn- resolve-suite [{:keys [tests extend exclude] :as raw-suite} resolved-suites]
  (let [excluded-set (set exclude)                          ;;TODO: is this required?
        included (mapcat (fn [included-suite-name]
                           (let [included-tests (get-in resolved-suites [included-suite-name :tests])]
                             (remove (fn [test] (contains? excluded-set (test-key test))) included-tests)))
                         extend)]
    {:tests (vec (concat included tests))}))

(defn- resolve-suite-extensions [raw-suites resolution-order]
  (reduce (fn [acc suite-name]
            (let [raw-suite (get raw-suites suite-name ::missing)]
              (if (= ::missing raw-suite)
                (throw (ex-info (format "Cannot resolve test suite %s" (name suite-name)) {:suites raw-suites :suite suite-name}))
                (let [resolved (resolve-suite raw-suite acc)]
                  (assoc acc suite-name resolved)))))
          {}
          resolution-order))

(defn resolve-extensions [raw-suite]
  (let [resolution-order (get-resolution-order raw-suite)]
    (resolve-suite-extensions raw-suite resolution-order)))

(defn resolve-test-suites [test-suite-files]
  (let [raw (reduce (fn [acc suite-file]
                      (let [suite (load-test-suite suite-file)]
                        (merge-raw-test-suites acc suite)))
                    nil
                    test-suite-files)]
    (resolve-extensions raw)))

(defn suite-tests [suite]
  (mapcat :tests (vals suite)))
