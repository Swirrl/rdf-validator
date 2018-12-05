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
  :suite3 {:import [:suite1 :suite2]
           :exclude [:suite1/test1]
           :tests [{:source "test4.txt"
                    :type :sparql}]}
}
```

Test suites can import any number of other suites - this includes each test from the referenced suite into the importing suite. Any tests defined 
in the imported suites can be selectively excluded by referencing them in the `:exclude` list. Each entry should contain a keyword of the form
`:suite-name/test-name`. By default test names are the stem of the file name up to the file extension e.g. the test for file `"test1.sparql"`
will be named `"test"`.

Test suite extensions must be acyclic e.g. `:suite1` importing `:suite2` which in turn imports `:suite1` is an error.
An error will be raised if any suite listed within an extension list is not defined, but suites do not need to be defined within the
same suite file. For example given two test files:

#### suite1.edn
```clojure
{:suite1 ["test1.sparql"]}
```

#### suite2.edn
```clojure
{:suite2 {:import [:suite1]
          :tests ["test2.sparql"]}}
```

this is valid as long as `suite1.edn` is provided as a suite whenever `suite2.edn` is required e.g.

    java -jar rdf-validator-standalone.jar --endpoint data.ttl --suite suite1.edn --suite suite2.edn
    
### Locating suites on the Java classpath

In addition to test suites explicitly provided through the `--suite` parameter, rdf-validator also searches the classpath for test
suite EDN definitions. The searched test suite files should be called `rdf-validator-suite.edn` and follow the format detailed above.
When running from the command line, the containing directory should be added to the Java classpath using the `-classpath` option.
Given an `rdf-validator-suite.edn` file:

#### rdf-validator-suite.edn
```clojure
{:cp-suite ["test1.sparql"
            "test2.sparql"]}
```

If this file is placed alongside the referenced `test1.sparql` and `test2.sparql` files in the directory `/tmp/rdf/my-suite` it can
be run as follows:

    java -cp "/tmp/rdf/my-suite:rdf-validator-standalone.jar" clojure.main -m rdf-validator.core --endpoint data.ttl

Use of the `-jar` option overrides any specified `-classpath` value, so the command above explicitly adds `rdf-validator.jar` to
the classpath and invokes `clojure.main` instead (which in turn executes the `rdf-validator` main method).

### Running via the Clojure tool

Manually building a Java classpath as shown above is tedious and error-prone. The [Clojure command-line tool](https://clojure.org/reference/deps_and_cli)
can automate the generation of the classpath and allows test suite directories to be packaged an distributed through `.jar` files or
remote `git` repositories. To run `rdf-validator` through the `clojure` tool, first create a new directory containing a `deps.edn` file:

#### deps.edn
```clojure
{:deps {swirrl/rdf-validator {:local/root "/path/to/rdf-validator.jar"}
        suite {:local/root "/path/to/test/suite"}}
 :aliases {:rdf-validator {:main-opts ["-m" "rdf-validator.core"]}}}
```

The `/path/to/test/suite` directory should contain a `src` directory containing an `rdf-validator-suite.edn` file with the 
format described above. The `clojure` tool will put the `/path/to/test/suite/src` directory on the java classpath and the 
`:rdf-validator` alias will invoke `clojure.main` with the required arguments.

Now `rdf-validator` can be run with:

    clj -A:rdf-validator --endpoint data.ttl
    
This will run the test cases defined in `/path/to/test/suite/src/rdf-validator-suite.edn`

The `suite` dependency does not necessarily need to be defined locally. The `clojure` tool allows dependencies to be specified
in remote `git` repositories or `.jar` files. If the test suite was hosted in a `git` repository instead, `deps.edn` could be
modified to refer to the desired commit. Similarly, the `rdf-validator` dependency can refer to a version on Github rather than
a local `.jar` file:

#### deps.edn
```clojure
{:deps {swirrl/rdf-validator {:git/url "https://github.com/Swirrl/rdf-validator.git" :sha "626f1df421f0feb5e78ca81c102f3120350c0dfd"}
        suite {:git/url "https://github.com/my/rdf/validator/suite" :sha "0f95c170d3799af13f51a5945339cae972866ff0"}}
 :aliases {:rdf-validator {:main-opts ["-m" "rdf-validator.core"]}}}
```

### Running individual suites

By default all test cases within all test suites will be executed when running `rdf-validator`.
This may be undesirable if many test suites are defined, or if one suite imports from another since
this will cause imported test cases to be executed multiple times.

Individual test suites can be executed by providing the suite names to be run in an argument list
to the command-line invocation e.g.

#### tests.edn
```clojure
{:suite1 ["test1.sparql" "test2.sparql" "test3.sparql"]
 :suite2 {:import [:suite1]
          :exclude [:suite1/test2]
          :tests ["test4.sparql"]
 :suite3 ["test5.sparql"]}
```

    java -jar rdf-validator-standalone.jar --endpoint data.ttl --suite tests.edn suite2 suite3
    
This will execute the tests defined within `suite2` and `suite3` within `tests.edn`.

    $ java -jar rdf-validator-standalone.jar --endpoint data.ttl --suite bad_predicate.sparql --variables variables.edn
     
## License

Copyright © 2018 Swirrl IT Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
