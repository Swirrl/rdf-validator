# rdf-validator

Runner for RDF test cases

## Installation

Install [leiningen](https://leiningen.org/) and then run

```
lein uberjar
```

This will build a standalone jar in the `target/uberjar` directory.

## Usage

`rdf-validator` runs a collection of test cases against a SPARQL endpoint. The endpoint can be either a HTTP(s) SPARQL
endpoint or a file or directory on disk. Test cases can be specified as either a SPARQL query file, or a directory
of such files.

The repository contains versions of the well-formed cube validation queries defined in the [RDF data cube specification](https://www.w3.org/TR/vocab-data-cube/#wf).
These are defined as SPARQL SELECT queries rather than the ASK queries defined in the specification to enable more detailed error reporting.

To run these tests against a local SPARQL endpoint:

    $ java -jar rdf-validator-standalone.jar --endpoint http://localhost/sparql/query --suite ./queries
    
This will run all test cases in the queries directory against the endpoint. Test cases can be run individually:

    $ java -jar rdf-validator-standalone.jar --endpoint http://localhost/sparql/query --suite ./queries/01_SELECT_Observation_Has_At_Least_1_Dataset.sparql
        
SPARQL endpoints can also be loaded from a file containing serialised RDF triples:

    $ java -jar rdf-validator-standalone.jar --endpoint data.ttl --suite ./queries
    
Multiple test cases can be specified:
    
    $ java -jar rdf-validator-standalone.jar --endpoint data.ttl --suite test1.sparql --suite test2.sparql 
        
The RDF dataset can also be specified:

    $ java -jar rdf-validator-standalone.jar --endpoint data.ttl --graph http://graph1 --graph http://graph2 --suite test1.sparql
    
Graphs are added a named graphs and included in the default graph. 

## Writing test cases

Test cases are expressed as either SPARQL ASK or SELECT queries. These queries are run against the target endpoint and the outcome of the test is based on the
result of the query execution.

### ASK queries

SPARQL ASK queries are considered to have failed if they evaluate to `true` so should be written to find invalid statements. 
This is consistent with the queries defined in the RDF data cube specification.

### SELECT queries

SPARQL SELECT queries are considered to have failed if they return any matching solutions. Like ASK queries they should return bindings describing invalid resources.

### Query variables

Validation queries can be parameterised with query variables which must be provided when the test suite is run. Query variables have the format `{{variable-name}}`
within a query file. For example to validate no statements exist with a specified predicate, the following query could be defined:

*bad_predicate.sparql*
```sparql
SELECT ?this WHERE {
  ?this <{{bad-predicate}}>> ?o .
}
```

when running this test case, the value of `bad-predicate` must be provided. This is done by providing an EDN file containing variable
bindings. The EDN document should contain a map from keywords to the corresponding string values e.g.

*variables.edn*
```clojure
{ :bad-predicate "http://to-be-avoided"
  :other-variable "http://other" }
```

the file of variable bindings is specified when running the test case(s) using the `--variables` parameter e.g.

    $ java -jar rdf-validator-standalone.jar --endpoint data.ttl --suite bad_predicate.sparql --variables variables.edn
     
## License

Copyright © 2018 Swirrl IT Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
