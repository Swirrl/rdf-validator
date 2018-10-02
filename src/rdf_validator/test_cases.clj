(ns rdf-validator.test-cases
  (:require [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.stuartsierra.dependency :as dep])
  (:import [java.io File]
           [java.net URL]
           [java.nio.file Paths]))

(defn- split-file-name-extension [^String file-name]
  (let [ei (.indexOf file-name ".")]
    (if (= -1 ei)
      [file-name nil]
      [(.substring file-name 0 ei) (.substring file-name (inc ei))])))

(defn- split-file-extension [^File f]
  (split-file-name-extension (.getName f))
  (let [n (.getName f)
        ei (.indexOf n ".")]
    (if (= -1 ei)
      [n nil]
      [(.substring n 0 ei) (.substring n (inc ei))])))

(defn get-file-extension [^File f]
  (second (split-file-extension f)))

(defn- load-file-test [suite ^File f]
  (let [[n ext] (split-file-extension f)
        type (some-> ext keyword)]
    (if (= :sparql type)
      {:type type
       :source f
       :name n
       :suite suite})))

(defmulti load-file-test-suite (fn [f] (some-> (get-file-extension f) keyword)))

(defmethod load-file-test-suite :sparql [f]
  {:user {:tests [{:type :sparql
                   :source f
                   :name (first (split-file-extension f))
                   :suite :user}]}})

(defn- read-edn-suite [f]
  (edn/read-string {:readers {'resource io/resource}} (slurp f)))

(defn- url? [x]
  (instance? URL x))

(defn- resource-url->test [suite-name ^URL url]
  (let [path (Paths/get (.toURI url))
        [n ext] (split-file-name-extension (str (.getFileName path)))
        type (some-> ext keyword)]
    (if (= :sparql type)
      {:type type
       :source url
       :name n
       :suite suite-name})))

(defn- normalise-test [test suite-file suite-name]
  (cond
    (string? test)
    (let [suite-dir (.getParentFile suite-file)
          test-file (io/file suite-dir test)]
      (if-let [test-def (load-file-test suite-name test-file)]
        test-def
        (throw (ex-info "Invalid test definition" {:test test :suite suite-name}))))

    (url? test)
    (resource-url->test suite-name test)

    (map? test)
    (throw (ex-info "implement" {}))

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
                   [(load-file-test suite f)]))
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
