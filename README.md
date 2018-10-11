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

## Defining test suites

A test suite defines a group of tests to be run. A test suite can be created from a single test file or a directory containing test files as shown in the
examples above. A test suite can also be defined within an EDN file that lists the tests it contains. The minimal form of this EDN file is:

```clojure
{
  :suite-name ["test1.sparql"
               "dir/test2.sparql"]
  :suite2     ["suite2/test3.sparql"]
}
```

Each key in the top-level map defines a test suite and the corresponding value contains the suite definition. Each test definition in the associated
list should be a path to a test file relative to the suite definition file. The type and name of each test is derived from the test file name. These
can be stated explicitly by defining tests within a map:

```clojure
{
  :suite-name [{:source "test1.sparql"
                :type :sparql
                :name "first"}
               {:source "test2.sparql"
                :name "second"}
               {:source "test3.sparql"}
               "dir/test4.sparql"]
}
```

When defining test definitions explicitly, only the `:source` key is required, the type and name will be derived from the test file name if not
provided. The two styles of defining tests can be combined within a test suite definition as defined above.

### Combining test suites

Test suites can selectively include test cases from other test suites:

```clojure
{
  :suite1 ["test1.sparql"
           "test2.sparql"]
  :suite2 ["test3.sparql"]
  :suite3 {:extend [:suite1 :suite2]
           :exclude [:suite1/test1]
           :tests [{:source "test4.txt"
                    :type :sparql}]}
}
```

Test suites can extend any number of other suites - this includes each test from the referenced suite into the extending suite. Any tests defined 
in the imported suites can be selectively excluded by referencing them in the `:exclude` list. Each entry should contain a keyword of the form
`:suite-name/test-name`. By default test names are the stem of the file name up to the file extension e.g. the test for file `"test1.sparql"`
will be named `"test"`.

Test suite extensions must be acyclic e.g. `:suite1` extending `:suite2` which in turn extends `:suite1` would be an error.
An error will be raised if any suite listed within an extension list is not defined, but suites do not need to be defined within the
same suite file. For example given two test files:

#### suite1.edn
```clojure
{:suite1 ["test1.sparql"]}
```

#### suite2.edn
```clojure
{:suite2 {:extend [:suite1]
          :tests ["test2.sparql"]}}
```

this is valid as long as `suite1.edn` is provided as a suite whenever `suite2.edn` is required e.g.

    java -jar rdf-validator-standalone.jar --endpoint data.ttl --suite suite1.edn --suite suite2.edn


## License

Copyright © 2018 Swirrl IT Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
