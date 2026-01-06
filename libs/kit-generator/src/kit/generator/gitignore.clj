(ns kit.generator.gitignore
  (:require [kit.generator.io :as io]
            [clojure.java.io :as jio])
  (:import [org.eclipse.jgit.ignore FastIgnoreRule]))

(defn load-gitignore [gitignore-path & {:keys [safe?]}]
  (if (or (not safe?) (io/exists? gitignore-path))
    (with-open [is (jio/reader gitignore-path)]
      {:rules
       (doall (for [rule (conj (line-seq is) ".git")]
                (FastIgnoreRule. rule)))})
    {:rules []}))

(defn ignored? [{:keys [rules]} path]
  (some #(.isMatch % path false false) rules))

(comment
  (.. (FastIgnoreRule. "log")
      (isMatch "log/log.txt" false, true))
  (.. (FastIgnoreRule. "log/")
      (isMatch "log/log.txt" true true))

  (println (slurp "../../../kit-pocketbase-example/.gitignore"))
  (def gitignore (load-gitignore "../../../kit-pocketbase-example/.gitignore"))

  (doseq [rule (seq (.getRules gitignore))]
    (println (.toString rule)))

  (ignored? gitignore "slog/foo.txt")
  (ignored? gitignore "../../../kit-pocketbase-example/log/foo.txt")

  (ignored? gitignore "/.clj-kondo/.cache/v1/java/org.apache.commons.lang3.concurrent.MultiBackgroundInitializer.transit.json")
  (ignored? gitignore "../kit/target/")
  (ignored? gitignore ".cpcache")
  (ignored? gitignore "README.md")
  (def gitignore (load-gitignore ".gitignore"))

  (def gitignore (load-gitignore "bad" {:safe? true}))
  (ignored? gitignore "target/"))
