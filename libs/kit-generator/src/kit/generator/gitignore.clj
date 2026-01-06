(ns kit.generator.gitignore
  (:require
   [clojure.java.io :as jio]
   [clojure.string :as str]
   [kit.generator.io :as io])
  (:import
   [org.eclipse.jgit.ignore FastIgnoreRule]))

(defn- trim-trailing-slash [s]
  (if (str/ends-with? s "/")
    (subs s 0 (dec (count s)))
    s))

(defn default-rules []
  {:rules
   (mapv #(FastIgnoreRule. %)
         [".git"])})

(defn load-gitignore [gitignore-path & {:keys [safe?]}]
  (if (or (not safe?) (io/exists? gitignore-path))
    (with-open [is (jio/reader gitignore-path)]
      {:rules
       (let [{:keys [rules]} (default-rules)]
         (concat rules (doall (for [rule (line-seq is)]
                                (FastIgnoreRule. (trim-trailing-slash rule))))))})
    {:rules []}))

(defn ignored? [{:keys [rules]} path]
  (some #(.isMatch % path false false) rules))

(comment
  (.. (FastIgnoreRule. "log")
      (isMatch "log/log.txt" false, true))
  (.. (FastIgnoreRule. "log/")
      (isMatch "log/log.txt" true true))
  (.. (FastIgnoreRule. ".clj-kondo") (isMatch ".clj-kondo/" false, false))

  (println (slurp "../../../kit-pocketbase-example/.gitignore"))
  (def gitignore (load-gitignore "../../../kit-pocketbase-example/.gitignore"))

  (doseq [rule (seq (.getRules gitignore))]
    (println (.toString rule)))

  (ignored? gitignore "foo/.git/index.lock")
  (ignored? gitignore "log/foo.txt")
  (ignored? gitignore "../../../kit-pocketbase-example/log/foo.txt")

  (ignored? gitignore "/.clj-kondo/.cache/v1/java/org.apache.commons.lang3.concurrent.MultiBackgroundInitializer.transit.json")
  (ignored? gitignore "../kit/target/")
  (ignored? gitignore ".cpcache")
  (ignored? gitignore "README.md")
  (def gitignore (load-gitignore ".gitignore"))

  (def gitignore (load-gitignore "bad" {:safe? true}))
  (ignored? gitignore "target/"))
