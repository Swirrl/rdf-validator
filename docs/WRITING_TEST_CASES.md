# Writing test cases

Test cases are expressed as either SPARQL ASK or SELECT queries.

- `ASK` queries are considered to have failed if they evaluate to `true` so should be written to find invalid statements.
- `SELECT` queries are considered to have failed if they return any matching solutions.

We recommend that you prefer using `SELECT` queries as then the results provide an indication as to the cause of the problem.

For example this query is a port of [IC-1 from the RDF Data Cube specification](https://www.w3.org/TR/vocab-data-cube/#ic-1) into `SELECT` style. It will return any `qb:Observation`s that are not also in a `qb:dataSet`:

```sparql
PREFIX qb: <http://purl.org/linked-data/cube#>

SELECT (?obs AS ?obsWithNoDataset)
WHERE {
  {
    # Check observation has a data set
    ?obs a qb:Observation .
    FILTER NOT EXISTS { ?obs qb:dataSet ?dataset1 . }
  }
}
```

Some more example `SELECT` queries for validating RDF Data cubes can be [found here](https://github.com/Swirrl/pmd-rdf-validations/tree/master/pmd-qb/src/swirrl/validations/pmd-qb).

## Query templates

You can write SPARQL validations with handlebars-style templates that will be pre-processed with [selmer](https://github.com/yogthos/Selmer). This let's you include variables like `{{ myvariable }}` in your query templates. To set the values, pass a map of bindings in [edn](https://github.com/edn-format/edn) format using the `--variables` CLI option.

For example, if you have a `followers-have-names.sparql` template including the variable `person`:

```sparql
PREFIX schema: <https://schema.org/>

SELECT ?follower WHERE {
  ?follower schema:follows <{{ person }}> .

  FILTER NOT EXISTS { ?follower scheme:name ?name }
}
```

And a `person.edn` data file include the bindings in a map from the variable name (as a [keyword](https://github.com/edn-format/edn#keywords)) to a string value that will be interpolated in it's place:

```clojure
{:person "http://example.net/id/person/Ada"}
```

Then you can call it like:

    $ clojure -M:rdf-validator --suite followers-have-names.sparql --variables person.edn --endpoint http://some.domain/sparql/query
