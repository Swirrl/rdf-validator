# Writing test cases

Test cases are expressed as either SPARQL ASK or SELECT queries. These queries are run against the target endpoint and the outcome of the test is based on the
result of the query execution.

## ASK queries

SPARQL ASK queries are considered to have failed if they evaluate to `true` so should be written to find invalid statements.
This is consistent with the queries defined in the RDF data cube specification.

## SELECT queries

SPARQL SELECT queries are considered to have failed if they return any matching solutions. Like ASK queries they should return bindings describing invalid resources.

## Query variables

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
