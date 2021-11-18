# RDF Validator

A Simple runner for RDF test cases & validations.

RDF Validator runs a collection of test cases against a SPARQL endpoint. The endpoint can be either a HTTP(s) SPARQL endpoint or a file or directory of RDF files on disk. Test cases can be specified as either a SPARQL query file containing either
an `ASK` or a `SELECT` query, or a suite of such files with a suite manifest.

Main features:

- 👍 SPARQL `SELECT` or `ASK` queries as validations
- 👌🏾 Package suites as git dependencies with a simple manifest format
- 🏃 Run 3rd party validations as dependencies via git or maven dependencies (thanks to the Clojure CLI tools)
- 🏃🏾 Run validations against SPARQL endpoints or files of RDF
- 🚴 Optionally dynamically generate queries with handlebars-like [selmer](https://github.com/yogthos/Selmer) templates

## Quick start

The quickest way to get started is to use the Swirrl's [PMD RDF data validations](https://github.com/Swirrl/pmd-rdf-validations) project which builds upon this application.

This readme explains how to customise and develop your own validation suite.

## Installing and running the RDF Validator

The recommended way to use the RDF Validator is as a Clojure application (although you could [compile a jar](/docs/COMPILING.md) instead) which will allow you to include suites of validations from git that will be automatically fetched and installed on first usage, and cached thereafter.

You'll first need to install the [Clojure command line tools](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools).

Once clojure is installed you can create a new directory and add a `deps.edn` file declaring a dependency on the `swirrl/rdf-validator` application:

```clojure
{:aliases
 {:rdf-validator
  {:extra-deps
   { swirrl/rdf-validator {:git/url "https://github.com/Swirrl/rdf-validator.git"
                           :sha "fd848fabc5718f876f99ee4ee5a3f89ea8529571"}}
   :main-opts ["-m" "rdf-validator.core"]}}}
```

The clojure cli tool will fetch the application (so you won't need to `git clone` this repository) when you run it with the above `:rdf-validator` alias.

For example, to run a sparql test against a remote endpoint you can do:

    $ clojure -M:rdf-validator --suite mytest.sparql --endpoint http://my/sparql/endpoint

You can also have the validator load-up an in-memory sparql endpoint from a RDF file:

    $ clojure -M:rdf-validator --suite mytest.sparql --endpoint mycube.ttl

Or by recursing through a directory tree of RDF files:

    $ clojure -M:rdf-validator --suite mytest.sparql --endpoint /path/to/rdf

You can see more examples in the docs on [command-line usage](/docs/USAGE.md).

## SPARQL validations

Validations are written as SPARQL queries. We recommend that you write `SELECT` queries that will identify and describe the causes of validation failures. The docs explain more about [writing test cases](/docs/WRITING_TEST_CASES.md).

You can pass your `.sparql` files to the validator with a command-line option (here validating a file of RDF data):

    $ clojure -M:rdf-validator --suite test1.sparql --endpoint data.ttl

## Writing a validation suite

To provide more structure you may want to collate your tests into suites.

To do this you can put the files into a directory (`"src"`) with a manifest file `rdf-validator-suite.edn` at the root:

    myvalidator
    ├── deps.edn
    └── src
        ├── myorg
        │   └── mysuite
        │       ├── test1.sparql
        │       └── test2.sparql
        └── rdf-validator-suite.edn

The manifest should specify the suite name and the relative paths to the SPARQL files to include:

```clojure
{:suite-name ["myorg/mysuite/test1.sparql"
              "myorg/mysuite/test2.sparql"]}
```

You can also use the manifest to add labels and descriptions or to modularise and re-use tests. See the docs on [defining test suites](/docs/DEFINING_TEST_SUITES.md) for more.

You can pass this suite as a command-line option:

    $ clojure -M:rdf-validator --suite src --endpoint data.ttl

Or record it in your `deps.edn` file:

```clojure
{:aliases ;; as above
 :paths ["src"]}
```

This will mean your suite is included by default so you can omit that option when running the validator:

    $ clojure -M:rdf-validator --endpoint data.ttl


## Including other validation suites

You can include third-party validation suites (and indeed share your own for others to build upon) using Clojure's [deps](https://clojure.org/guides/deps_and_cli) tool by adding them as dependencies to the `deps.edn` file. Clojure deps supports [various ways of fetching and resolving dependencies](https://clojure.org/reference/deps_and_cli#_dependencies) and putting them on the classpath, such as via git, maven packaged jars, or just dependencies at a `:local/root`.

For example, we can include a specific version of the `qb` suite from [pmd-rdf-validations](https://github.com/Swirrl/pmd-rdf-validations) by extending your `deps.edn` to add a `:deps` key alongside the `:aliases`:

```clojure
{:aliases ;; as above
 :deps
 { swirrl/validations.qb {:git/url "git@github.com:Swirrl/pmd-rdf-validations.git"
                          :sha "63479f200a7c3d1b0e63bc43b2617181644c846b"
                          :deps/manifest :deps
                          :deps/root "qb"}}}
```

The `Swirrl/pmd-rdf-validations.git` repository contains multiple suites, each defined as their own dep within the same repo.  The `:deps/root` key essentially lets us point to a specific sub-directory, here for the `"qb"` (data cube) validations.

Once these are specified we can run them against a repository containing data cubes, e.g.

    $ clojure -M:rdf-validator --endpoint http://some.domain/sparql/query

Note that this command will first fetch the validation suite dependency, cache it locally for future use, and run all the validation suites we put on the classpath (here just the data cube validations).

## License

Copyright © 2018 Swirrl IT Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
