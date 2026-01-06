(ns kit.generator.io
  "I/O utility functions."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as jio]
   [clojure.test :as t])
  (:import
   java.io.File))

(defn str->edn [config]
  (edn/read-string {:default tagged-literal} config))

(defn edn->str [edn]
  (binding [*print-namespace-maps* false]
    (with-out-str (prn edn))))

(defn update-edn-file [path f]
  (spit
   path
   (-> (slurp path)
       (str->edn)
       (f)
       (edn->str))))

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
  [path base-path]
  (.toString (.relativize (.toURI (jio/file base-path))
                          (.toURI (jio/file path)))))

(defn clone-file
  "Copy file from `src` to `target`, creating parent directories as needed."
  [src tgt]
  (jio/make-parents tgt)
  (let [source-file (jio/file src)
        target-file (jio/file tgt)]
    (jio/copy source-file target-file)))

(defn clone-folder
  "Erase `target` then copy all files from `src` to `target` recursively."
  [src target & {:keys [filter] :or {filter (constantly true)}}]
  (delete-folder target)
  (let [files (file-seq (clojure.java.io/file src))]
    (doseq [f files]
      (let [target-file (clojure.java.io/file target
                                              (relative-path f src))]
        (when (and (.isFile f) (filter (.getPath f)))
          (clone-file f target-file))))))

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

(comment
  (t/run-tests 'kit.generator.io))
