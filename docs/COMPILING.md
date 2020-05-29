# Compiling

Rather than using via the [clojure CLI tools](https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools) it
is also possible to AOT compile the RDF Validator as an uberjar, and run with the incantation: `java -jar rdf-validator.jar`.

This has the small advantage that it reduces start up time a little, however it does also make it substantially harder to assemble dependencies via
the command line tools.  Hence this mechanism is no longer recommended.

To compile an uberjar though, you need to first install [leiningen](https://leiningen.org/) and then run:

```
lein uberjar
```

This will build a standalone jar in the `target/uberjar` directory.
