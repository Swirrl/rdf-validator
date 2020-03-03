(ns rdf-validator.util
  (:import [java.io File]
           [java.net URL URI]
           [java.nio.file Paths Path]))

(defn url?
  "Returns whether the given object is an instance of java.net.URL."
  [x]
  (instance? URL x))

(defprotocol HasPath
  (^Path get-path [this]
    "Returns the java.nio.file.Path associated with this instance."))

(defn get-file-name
  "Returns the file name for the contained path"
  [has-path]
  (str (.getFileName (get-path has-path))))

(def ^:private empty-string-array (make-array String 0))

(extend-protocol HasPath
  File
  (get-path [^File f] (.toPath f))

  URL
  (get-path [^URL url]
    (get-path (.toURI url)))

  URI
  (get-path [^URI uri]
    (Paths/get (.getPath uri) empty-string-array)))

(defn split-file-name-extension
  "Splits a file name string into a pair of [prefix extension] where extension is the remainder
   of the file name after the last . character. If the file name does not contain a . the returned
   extension is nil."
  [^String file-name]
  (let [ei (.lastIndexOf file-name ".")]
    (if (= -1 ei)
      [file-name nil]
      [(.substring file-name 0 ei) (.substring file-name (inc ei))])))

(defn split-file-extension
  "Splits the name of a HasPath object in the same way as split-file-name-extension."
  [source]
  (split-file-name-extension (get-file-name source)))

(defn get-file-extension
  "Returns the extension for an implementation of HasPath. The extension is the remainder of the file name after
   the last . character."
  [source]
  (second (split-file-extension source)))

(defn resources
  "Returns a sequence of URLs for resources with the specified name"
  ([resource-name]
    (resources resource-name (.getContextClassLoader (Thread/currentThread))))
  ([resource-name ^ClassLoader loader]
    (enumeration-seq (.getResources loader resource-name))))