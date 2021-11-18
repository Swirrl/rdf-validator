# Defining test suites

A test suite defines a group of tests to be run. You can call a the validator with a single file or a directory of files. A test suite can also be defined within an EDN file that lists the tests it contains. The minimal form of this EDN file is:

```clojure
{
  :suite-name ["test1.sparql"
               "dir/test2.sparql"]
  :suite2     ["suite2/test3.sparql"]
}
```

Each key in the top-level map defines a test suite and the corresponding value contains the suite definition. Each test definition in the associated list should be a path to a test file relative to the suite definition file. The type and name of each test is derived from the test file name. These can be stated explicitly by defining tests within a map:

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

When defining test definitions explicitly, only the `:source` key is required, the type and name will be derived from the test file name if not provided.

As described in the documentation on [writing test cases](/doc/WRITING_TEST_CASES.md), you can choose between `ASK` and `SELECT` queries. Both types can be combined within a test suite definition.

## Combining test suites

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

### suite1.edn
```clojure
{:suite1 ["test1.sparql"]}
```

### suite2.edn
```clojure
{:suite2 {:import [:suite1]
          :tests ["test2.sparql"]}}
```

this is valid as long as `suite1.edn` is provided as a suite whenever `suite2.edn` is required e.g.

    $ clojure -M:rdf-validator --endpoint data.ttl --suite suite1.edn --suite suite2.edn

### Running individual suites

By default all test cases within all test suites will be executed when running `rdf-validator`.
This may be undesirable if many test suites are defined, or if one suite imports from another since
this will cause imported test cases to be executed multiple times.

Individual test suites can be executed by providing the suite names to be run in an argument list
to the command-line invocation e.g.

```clojure
{:suite1 ["test1.sparql" "test2.sparql" "test3.sparql"]
 :suite2 {:import [:suite1]
          :exclude [:suite1/test2]
          :tests ["test4.sparql"]
 :suite3 ["test5.sparql"]}
```

    $ clojure -M:rdf-validator --endpoint data.ttl --suite tests.edn suite2 suite3

This will execute the tests defined within `suite2` and `suite3` within `tests.edn`.

    $ clojure -M:rdf-validator --endpoint data.ttl --suite bad_predicate.sparql --variables variables.edn
