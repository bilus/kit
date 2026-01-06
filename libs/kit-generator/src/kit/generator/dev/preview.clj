(ns kit.generator.dev.preview
  (:require
   [clojure.string :as str]
   [kit.api :as kit]
   [kit.generator.dev.diff :as diff]
   [kit.generator.gitignore :as gitignore]
   [kit.generator.io :as io]
   [kit.generator.modules :as modules]
   [nextjournal.beholder :as beholder]))

(defn- copy-project-files
  "Copies all project files to target directory, excluding files ignored by .gitignore."
  [project-root target-dir]
  ;; TODO: Slower than necessary; copy only modified files.
  (let [gitignore (gitignore/load-gitignore (io/concat-path project-root ".gitignore") {:safe? true})
        ctx       (kit/read-ctx (io/concat-path project-root "kit.edn"))]
    (io/clone-folder project-root target-dir
                     ;; TODO: Not 100% sure we should indeed use .gitignore.
                     ;; Case in point: node_modules. Should we ignore them?
                     ;; 1. Pro: Copying node_modules is slow.
                     ;; 2. Con: Less accurate preview.
                     ;; 3. Con: Slower npm install
                     {:filter (fn [path]
                                (not (gitignore/ignored? gitignore path)))})
    ;; modules are always copied even if ignored by .gitignore
    (let [new-ctx (kit/read-ctx (io/concat-path target-dir "kit.edn"))]
      (io/clone-folder (modules/root ctx) (modules/root new-ctx)))))

(defn preview-install
  "Previews the installation of a module by copying the current project files to a
   temporary directory and simulating the installation there. Overwrites project
   modules with modules specified in local-modules as if they were synced. These
   would typically contain modules under development, with changes that need to
   be tested."
  [preview-folder local-modules module-key kit-edn-path opts]
  (let [source-folder (io/parent-name kit-edn-path)]
    (copy-project-files source-folder preview-folder)
    (let [new-kit-edn-path (io/concat-path preview-folder "kit.edn")
          ctx              (kit/read-ctx new-kit-edn-path)]
      (doseq [{:keys [name path]} local-modules]
        (io/clone-folder path
                         (io/concat-path (modules/root ctx) name)))
      (kit/install-module module-key new-kit-edn-path opts))))

(defn watch [{:keys [project-dir preview-dir]} module-repos module opts]
  (let [diff-html-file "kit-preview-diff.html"
        diff-html-path (io/concat-path preview-dir diff-html-file)
        preview        #(try
                          (preview-install preview-dir module-repos module (io/concat-path project-dir "kit.edn") opts)
                          (->> (diff/diff-folders project-dir preview-dir)
                               (remove (fn [patch] (= diff-html-file (:name patch))))
                               (diff/write-diff-page diff-html-path))
                          (println "Open" diff-html-path "to see the preview diff.")
                          (catch Exception e
                            (println "Error during preview installation:" (.getMessage e))
                            (println "Stack trace")
                            (.printStackTrace e)))
        previewer      (apply beholder/watch (fn [_]
                                               (preview))
                              project-dir
                              (map :path module-repos))]
    (preview)
    #(beholder/stop previewer)))

(comment
  (kit/install-module :kit/karma "../../../kit-pocketbase-example/kit.edn" {:accept-hooks? true})

  (->> (diff/diff-folders "../../../kit-pocketbase-example" "/tmp/preview")
       (diff/write-diff-page "/tmp/preview/kit-preview-diff.html"))

  (def w (watch {:project-dir "../../../kit-pocketbase-example"
                 :preview-dir "/tmp/preview"}
                [{:path "../../../kit-modules"
                  :name "bilus-modules"}]
                :kit/starter
                {:accept-hooks? false}))
  (println "foo")
  (w) ;; stop
;
  )
