(defproject swirrl/rdf-validator "0.2.0"
  :description "Tool for validating RDF data"
  :url "https://github.com/Swirrl/rdf-validator"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["apache-releases" {:url "https://repository.apache.org/content/repositories/releases/"}]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.7"]
                 [grafter "0.11.5"]
                 [org.apache.jena/apache-jena-libs "3.8.0" :extension "pom"]
                 [selmer "1.12.0"]
                 [org.slf4j/slf4j-api "1.7.25"]

                 ;;TODO: move logging dependencies when exposing as a library!
                 [org.apache.logging.log4j/log4j-api "2.11.0"]
                 [org.apache.logging.log4j/log4j-core "2.11.0"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.11.0"]]
  :main ^:skip-aot rdf-validator.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
