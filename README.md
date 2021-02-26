# RDF Validator

A Simple runner for RDF test cases & validations.

RDF Validator runs a collection of test cases against a SPARQL endpoint. The endpoint can be either a HTTP(s) SPARQL
endpoint or a file or directory of RDF files on disk. Test cases can be specified as either a SPARQL query file containing either
an `ASK` or a `SELECT` query, or a suite of such files with a suite manifest.

Main features:

- 👍 SPARQL `SELECT` or `ASK` queries as validations
- 👌🏾 Package suites as git dependencies with a simple manifest format
- 🏃 Run 3rd party validations as dependencies via git or maven dependencies (thanks to the Clojure CLI tools)
- 🏃🏾 Run validations against SPARQL endpoints or files of RDF
- 🚴 Optionally dynamically generate queries with handlebars-like [selmer](https://github.com/yogthos/Selmer) templates

## Quick Start

The recomended way to install and run the RDF Validator as an application is via the Clojure command line tools.

The advantage to this method is that it provides an advanced way to include suites of validations as git deps, that
will be automatically fetched and installed on first usage, and cached thereafter.  This means you can use Clojure's
`deps.edn` file to fetch suites of validations from 3rd parties easily.

To do this follow these steps:

First [Install the clojure CLI tools](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools).

Then specify a `deps.edn` file like this:

```clojure
{
 :aliases {:rdf-validator {:extra-deps { swirrl/rdf-validator {:git/url "https://github.com/Swirrl/rdf-validator.git"
                                                               :sha "fd848fabc5718f876f99ee4ee5a3f89ea8529571"}}
                           :main-opts ["-m" "rdf-validator.core"]}
                           }
 }
```

This then lets you run the command line validator like so:

    $ clojure -A:rdf-validator <ARGS>

You'll then want to configure some validation suites and supply it with the location of some RDF (either via a SPARQL endpoint) or as a file of triples.

### Including a test suite

The easiest way to include a test suite is to include an existing one as a dependency in your `deps.edn`.  `deps.edn` supports
[various ways of fetching and resolving dependencies](https://clojure.org/reference/deps_and_cli#_dependencies) and putting them
on the classpath, such as via git deps, maven packaged jars, or just dependencies at a `:local/root`.

To do this we can include a 3rd party suite [such as those found in this repo](https://github.com/Swirrl/pmd-rdf-validations) like this:

```clojure
 {:deps {;; NOTE each dep here is a validation suite
         swirrl/validations.qb {:git/url "git@github.com:Swirrl/pmd-rdf-validations.git"
                                :sha "63479f200a7c3d1b0e63bc43b2617181644c846b"
                                :deps/manifest :deps
                                :deps/root "qb"}
        }
 :aliases {:rdf-validator {:extra-deps { swirrl/rdf-validator {:git/url "https://github.com/Swirrl/rdf-validator.git"
                                                               :sha "c85338c44be9f7f9726c30dca4aa47ef8bd9cfe6"}}
                           :main-opts ["-m" "rdf-validator.core"]}
                     }
 }
```

This particular repository contains multiple suites, each defined as their own dep within the same repo.  The `:deps/root` key essentially
lets us point to a directory containing a dep, here the dep is a copy of the [integrity constraints](https://www.w3.org/TR/vocab-data-cube/#wf-rules)
from the [RDF Data Vocabulary](https://www.w3.org/TR/vocab-data-cube/).

Once these are specified we can run them against a repository containing data cubes, e.g.

    $ clojure -A:rdf-validator --endpoint http://some.domain/sparql/query

Note that this command will first fetch the validation suite dependency, cache it locally for future use, and run all the validation suites
we put on the classpath (here just the data cube validations).

### Writing your own validation suites

Validations can be supplied on the command line as just a directory of `.sparql` files, or specified on the JVMs classpath via your `deps.edn` file.

Here we demonstrate writing a simple classpath suite, as it is the easiest way to manage suites of validations that can be included as libraries.  Other
supported methods are described in the more detailed docs.

To do this first add a `:paths ["src"]` key to your `deps.edn`:

```clojure
 {:paths ["src"]
  :deps {;; NOTE each dep here is a validation suite
         swirrl/validations.qb {:git/url "git@github.com:Swirrl/pmd-rdf-validations.git"
                                :sha "63479f200a7c3d1b0e63bc43b2617181644c846b"
                                :deps/manifest :deps
                                :deps/root "qb"}
        }
 :aliases {:rdf-validator {:extra-deps { swirrl/rdf-validator {:git/url "https://github.com/Swirrl/rdf-validator.git"
                                                               :sha "fd848fabc5718f876f99ee4ee5a3f89ea8529571"}}
                           :main-opts ["-m" "rdf-validator.core"]}
                     }

 }
```

This essentially says when running the validator to include the `"src"` directory on the JVM's classpath.  Next create the suite with the following
directory structure:

    /your/validation/repo
      |---- deps.edn
      |---- src
              |---- rdf-validator-suite.edn
              |---- myorg
                    |---- mysuite
                          |---- test1.sparql
                          |---- test2.sparql

Then in the `rdf-validator-suite.edn` file which must be at a classpath root (i.e. at the root of the "src" directory) specify the suites name and the relative paths
to the SPARQL files to include the suite, e.g.

```clojure
{
  :suite-name ["myorg/mysuite/test1.sparql"
               "myorg/mysuite/test2.sparql"]
}
```

Next write your SPARQL validations and run like so:

    $ clojure -A:rdf-validator --endpoint http://some.domain/sparql/query

[More on defining test suites](/docs/DEFINING_TEST_SUITES.md)

### Writing SPARQL validations

Validations are written as either SPARQL `SELECT` queries which should find and return validation failures, or
ASK queries which fail when returning `false`.

We recommend prefering the `SELECT` style as they provide more information to users on what went wrong.  For example
this query is a port of IC-1 from the RDF Datacube spec into `SELECT` style.

It will return any `qb:Observation`s that are not also in a `qb:dataSet`:

```sparql
PREFIX qb:      <http://purl.org/linked-data/cube#>

SELECT (?obs AS ?obsWithNoDataset)
WHERE {
  {
    # Check observation has a data set
    ?obs a qb:Observation .
    FILTER NOT EXISTS { ?obs qb:dataSet ?dataset1 . }
  }
}
```

Some more example `SELECT` queries for validating RDF Data cubes can be [found here](https://github.com/Swirrl/pmd-rdf-validations/tree/master/pmd-qb/src/swirrl/validations/pmd-qb)

Additionally RDF Validator supports an advanced feature which usually needn't be used, to pre-process queries with [selmer](https://github.com/yogthos/Selmer) by replacing "handlebars like" variables (e.g `{{dataset-uri}}`) with any bound `--variables` provided via an `.edn` map of bindings, e.g.

```clojure
{:dataset-uri "http://my.domain/data/my-dataset"}
```

[More on writing test cases](/docs/WRITING_TEST_CASES.md)

## Usage

[More on command line options and usage](/docs/USAGE.md)

## License

Copyright © 2018 Swirrl IT Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
