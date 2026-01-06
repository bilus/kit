(ns kit-generator.io
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as jio]
   [kit.generator.io :as io]
   [clojure.set :as set]
   [clojure.test :as t])
  (:import
   [java.nio.file Files]
   [java.nio.file.attribute FileAttribute]))

(defn ls-R
  "Walks dir recursively and returns a map of relative file paths to their contents."
  [dir]
  (let [root (jio/file dir)
        root-path (.toPath root)]
    (->> (file-seq root)
         (filter #(.isFile %))
         (reduce (fn [acc f]
                   (let [rel-path (str (.relativize root-path (.toPath f)))]
                     (assoc acc rel-path (slurp f))))
                 {}))))

(def delete-folder io/delete-folder)
(def relative-path io/relative-path)
(def clone-file io/clone-file)
(def clone-folder io/clone-folder)
(def concat-path io/concat-path)

(defn- file-mismatches
  "Checks content against expectation. Returns set of errors or nil if matches."
  [content expectation]
  (cond
    (string? expectation)
    (when (not= content expectation)
      #{"content mismatch"})

    (seq expectation)
    (let [failed (keep (fn [regex]
                         (when-not (re-find regex content)
                           (str "regex not found: " regex)))
                       expectation)]
      (when (seq failed)
        (set failed)))

    (empty? expectation) ; [] means any content is acceptable
    nil

    :else (throw (ex-info "Unsupported expectation type" {:expectation expectation}))))

(defn folder-mismatches
  "Compares directory contents against expectations map.
   Map of path -> set of errors, or empty map if all match."
  {:test (fn []
           (let [dir "test/resources/snippets"]
             (t/are [expectations opts mismatches] (= mismatches (folder-mismatches dir expectations opts))
               (ls-R dir)                    {}                    {}
               (-> (ls-R dir)
                   (dissoc "kit/routing.md")
                   (assoc "foo/bar.txt"
                          "X"))              {}                    {"kit/routing.md" #{"unexpected file"}
                                                                    "foo/bar.txt"    #{"file missing"}}
               (-> (ls-R dir)
                   (assoc "kit/routing.md"
                          #{#"reitit"}))     {}                    {}
               (-> (ls-R dir)
                   (assoc "kit/routing.md"
                          [#"NOMATCH"]))     {}                    {"kit/routing.md" #{"regex not found: NOMATCH"}}
               (-> (ls-R dir)
                   (assoc "foo.txt"
                          [#"NOMATCH"]))     {:filter
                                              #(not= "foo.txt" %)} {})))}
  [dir expectations & {filter-fn :filter :or {filter-fn (constantly true)}}]
  (let [actual         (ls-R dir)
        expected-paths (set (keys expectations))
        actual-paths   (set (keys actual))
        missing        (set/difference expected-paths actual-paths)
        extra          (set/difference actual-paths expected-paths)
        content-errors (reduce (fn [acc [path expectation]]
                                 (if-let [errors (some-> (get actual path)
                                                         (file-mismatches expectation))]
                                   (assoc acc path errors)
                                   acc))
                               {}
                               expectations)]
    (->> (cond-> content-errors
           (seq missing) (merge (zipmap missing (repeat #{"file missing"})))
           (seq extra)   (merge (zipmap extra (repeat #{"unexpected file"}))))
         (filter (comp filter-fn first))
         (into {}))))

(defn write-file
  "Writes contents to target file, creating parent directories as needed."
  [contents target]
  (jio/make-parents target)
  (->> contents
       (spit target)))

(defn create-files
  "Creates files in dir from a map of relative paths to contents.
   Inverse of ls-R."
  [dir files]
  (doseq [[path contents] files]
    (write-file contents (concat-path dir path))))

(defn write-edn
  "Writes EDN contents to target file, creating parent directories as needed."
  [data target]
  (-> data
      (io/edn->str)
      (write-file target)))

(defn read-safe [path]
  (when (.exists (jio/file path))
    (slurp path)))

(defn read-edn-safe [path]
  (when-let [content (read-safe path)]
    (edn/read-string content)))

(defmacro with-temp-dir
  "Creates a temporary directory, binds it to `binding`, executes `body`,
   and deletes the directory afterwards."
  [[binding prefix] & body]
  `(let [temp-dir# (Files/createTempDirectory ~prefix (into-array FileAttribute []))
         ~binding (str temp-dir#)]
     (try
       ~@body
       (finally
         (delete-folder ~binding)))))

(comment
  (t/run-all-tests)
  (t/run-tests 'kit-generator.io))
