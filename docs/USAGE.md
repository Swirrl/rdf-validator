# Usage (command line options)

RDF Validator runs a collection of test cases against a SPARQL endpoint. The endpoint can be either a HTTP(s) SPARQL
endpoint or a file or directory on disk. Test cases can be specified as either a SPARQL query file, or a directory
of such files.

The repository contains versions of the well-formed cube validation queries defined in the [RDF data cube specification](https://www.w3.org/TR/vocab-data-cube/#wf).
These are defined as SPARQL SELECT queries rather than the ASK queries defined in the specification to enable more detailed error reporting.

To run these tests against a local SPARQL endpoint:

    $ clojure -A:rdf-validator --endpoint http://localhost/sparql/query --suite ./queries

This will run all test cases in the queries directory against the endpoint. Test cases can be run individually:

    $ clojure -A:rdf-validator --endpoint http://localhost/sparql/query --suite ./queries/01_SELECT_Observation_Has_At_Least_1_Dataset.sparql

SPARQL endpoints can also be loaded from a file containing serialised RDF triples:

    $ clojure -A:rdf-validator --endpoint data.ttl --suite ./queries

Multiple test cases can be specified:

    $ clojure -A:rdf-validator --endpoint data.ttl --suite test1.sparql --suite test2.sparql

The RDF dataset can also be specified:

    $ clojure -A:rdf-validator --endpoint data.ttl --graph http://graph1 --graph http://graph2 --suite test1.sparql

Graphs are added a named graphs and included in the default graph.
