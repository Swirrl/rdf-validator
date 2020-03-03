(defproject swirrl/rdf-validator "0.3.0-SNAPSHOT"
  :description "Tool for validating RDF data"
  :url "https://github.com/Swirrl/rdf-validator"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["apache-releases" {:url "https://repository.apache.org/content/repositories/releases/"}]]
  :plugins [[lein-tools-deps "0.4.1"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :project]}
  :main ^:skip-aot rdf-validator.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:resource-paths ["test/resources"]}})
