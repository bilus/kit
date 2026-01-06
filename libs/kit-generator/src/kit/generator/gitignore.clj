(ns kit.generator.gitignore
  (:require [kit.generator.io :as io])
  (:import [org.eclipse.jgit.ignore IgnoreNode]
           [java.io FileInputStream]))

(defn load-gitignore [gitignore-path & {:keys [safe?]}]
  (let [node (IgnoreNode.)]
    (when (or (not safe?) (io/exists? gitignore-path))
      (with-open [is (FileInputStream. gitignore-path)]
        (.parse node is)))
    node))

(defn ignored? [^IgnoreNode node path]
  (true? (.checkIgnored node path (io/directory? path))))

(comment

  (def gitignore (load-gitignore "../../.gitignore"))
  (ignored? gitignore "../kit/target/")
  (ignored? gitignore ".cpcache")
  (ignored? gitignore "README.md")

  (def gitignore (load-gitignore "bad" {:safe? true}))
  (ignored? gitignore "target/"))
