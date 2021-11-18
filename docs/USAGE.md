# Usage (command line options)

RDF Validator runs a collection of test cases against a SPARQL endpoint.

The endpoint can be either a HTTP(s) SPARQL endpoint or a file or directory on disk.

Test cases can be specified as either a SPARQL query file, or a directory of such files.

To run a directory of tests against a SPARQL endpoint:

	$ clojure -A:rdf-validator --endpoint http://localhost/sparql/query --suite ./queries

To run test cases individually:

	$ clojure -A:rdf-validator --endpoint http://localhost/sparql/query --suite ./queries/01_SELECT_Observation_Has_At_Least_1_Dataset.sparql

Multiple individual test cases can be specified:

	$ clojure -A:rdf-validator --endpoint http://localhost/sparql/query --suite test1.sparql --suite test2.sparql

To load an in-memory SPARQL endpoints from a file containing serialised RDF triples:

	$ clojure -A:rdf-validator --endpoint data.ttl --suite ./queries

You can also load data from a directory of RDF files:

	$ clojure -A:rdf-validator --endpoint ./data --suite ./queries

The RDF dataset can also be specified:

	$ clojure -A:rdf-validator --endpoint ./data --suite ./queries --graph http://graph1 --graph http://graph2

Graphs are added a named graphs and included in the default graph.
