(ns kit.generator.io
  "I/O utility functions."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as jio]
   [clojure.test :as t])
  (:import
   [java.io File]
   [java.nio.file
    CopyOption
    Files
    Path
    StandardCopyOption]))

(defn str->edn [config]
  (edn/read-string {:default tagged-literal} config))

(defn edn->str [edn]
  (binding [*print-namespace-maps* false]
    (with-out-str (prn edn))))

(defn concat-path
  "Joins `head` and one or more `parts` using path separators specific to the particular
   operating system. Ignores parts that are `nil`."
  [head & parts]
  (->> parts
       (reduce (fn [path p]
                 (if p
                   (File. path p)
                   (File. path)))
               head)
       .getPath))

(defn delete-folder [file-name]
  (letfn [(func [f]
            (when (.exists f)
              (when (.isDirectory f)
                (doseq [f2 (.listFiles f)]
                  (func f2)))
              (jio/delete-file f)))]
    (func (jio/file file-name))))

(defn relative-path
  "Returns path as relative to base-path"
  {:test (fn []
           (t/is (= "docs/file.txt"
                    (relative-path "/home/user/docs/file.txt" "/home/user")))
           (t/is (= "docs/file.txt"
                    (relative-path "home/user/docs/file.txt" "home/user")))
           (t/is (= "/FOO/file.txt"
                    (relative-path "/FOO/file.txt" "/BAR")))
           (t/is (= "file.txt"
                    (relative-path "file.txt" "."))))}
  [path base-path]
  (.getPath (.relativize (.toURI (jio/file base-path))
                         (.toURI (jio/file path)))))
(defn- copy-file
  "Copy file from `src` to `target`."
  [src-file tgt-file]
  (with-open [in (jio/input-stream src-file)
              out (jio/output-stream tgt-file)]
    (jio/copy in out)))

(defn clone-file
  "Copy file from `src` to `target`, creating parent directories as needed."
  [src tgt]
  (jio/make-parents tgt)
  (let [src-file (jio/file src)
        tgt-file (jio/file tgt)]
    ;; Using Files/copy with COPY_ATTRIBUTES triggers DirectoryWatcher who
    ;; thinks the source file was created.
    (copy-file src-file tgt-file)
    (.setLastModified tgt-file (.lastModified src-file))))

(defn clone-folder
  "Erase `target` then copy all files from `src` to `target` recursively,
   preserving last modified timestamps.

   Options:
     :filter - predicate called with file path string, return true to include the file"
  [src-folder target-folder & {:keys [filter] :or {filter (constantly true)}}]
  (delete-folder target-folder)
  ;; TODO: It can be optimized by walking the file tree and skipping directories
  ;; that don't match the filter.
  (let [files (file-seq (clojure.java.io/file src-folder))]
    (doseq [src files]
      (let [tgt (clojure.java.io/file target-folder (relative-path src src-folder))]
        (when (filter (.getPath src))
          (if (.isDirectory src)
            (when-not (.exists tgt)
              (.mkdir tgt))
            ;; Using Files/copy with COPY_ATTRIBUTES triggers DirectoryWatcher who
            ;; thinks the source file was created.
            (copy-file src tgt))
          (.setLastModified tgt (.lastModified src)))))))

(defn exists?
  [path]
  (.exists (jio/file path)))

(defn same-path?
  "Returns true if two paths point to the same file or directory."
  [path1 path2]
  (= (.getCanonicalPath (jio/file path1))
     (.getCanonicalPath (jio/file path2))))

(defn directory?
  [path]
  (.isDirectory (jio/file path)))

(defn parent-name
  "Returns the parent directory name of the file or directory at `path`."
  {:test (fn []
           (t/is (= "/home/user/docs"
                    (parent-name "/home/user/docs/file.txt")))
           (t/is (= "home/user/docs"
                    (parent-name "home/user/docs/file.txt")))
           (t/is (= "."
                    (parent-name "file.txt"))))}
  [path]
  (or (.getParent (jio/file path))
      "."))

;; TODO: Functions like this should probably use canonicalized paths to avoid issues with relative paths.
(defn parent? [base-dir path]
  (= base-dir (parent-name path)))

(defn last-modified
  "Returns the last modification timestamp of the file at `path` in milliseconds."
  [path]
  (.lastModified (jio/file path)))

(defn newer?
  "Returns true if the file at `path1` was modified more recently than the file at `path2`."
  [path1 path2]
  (> (last-modified path1) (last-modified path2)))

(defn binary-file? [path]
  (let [mime (Files/probeContentType (.toPath (jio/file path)))]
    (and mime (not (.startsWith mime "text/")))))

(comment
  (binary-file? "/tmp/preview/resources/public/img/kit.png")
  (binary-file? "/tmp/preview/README.md")
  (binary-file? "/tmp/preview/package.json")
  (doseq [f  (take 10 (file-seq (jio/file "/tmp/preview")))]
    (println (str f)))
  (clone-folder "../../../kit-pocketbase-example/" "/tmp/preview2")
  (.isDirectory (jio/file "../../../kit-pocketbase-example"))
  (t/run-tests 'kit.generator.io))
