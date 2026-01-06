(ns kit.generator.dev.preview
  (:require
   [babashka.process :as proc]
   [clojure.core.async :as async]
   [kit.api :as kit]
   [kit.generator.dev.diff :as diff]
   [kit.generator.dev.watcher :as watcher]
   [kit.generator.gitignore :as gitignore]
   [kit.generator.io :as io]
   [kit.generator.modules :as modules]
   [kit.generator.reporter :as reporter]))

(defn- copy-project-files
  "Copies all project files to target directory, excluding files ignored by .gitignore."
  [project-root target-dir]
  ;; TODO: Slower than necessary; copy only modified files.
  (let [gitignore (gitignore/load-gitignore (io/concat-path project-root ".gitignore") {:safe? true})
        ctx       (kit/read-ctx (io/concat-path project-root "kit.edn"))
        not-gitignored    (fn [gitignore path]
                            (not (gitignore/ignored? gitignore path)))
        ;; node_modules/.bin contains symlinks that Java's delete-file can't handle.
        ;; Use shell rm -rf to reliably remove it before clone-folder tries to delete.
        node-modules-path (io/concat-path target-dir "node_modules")]
    (when (io/exists? node-modules-path)
      (println "** removing existing node_modules at" node-modules-path)
      (proc/shell "rm" "-rf" node-modules-path))
    (io/clone-folder project-root target-dir
                     ;; TODO: Not 100% sure we should indeed use .gitignore.
                     ;; Case in point: node_modules. Should we ignore them?
                     ;; 1. Pro: Copying node_modules is slow.
                     ;; 2. Con: Less accurate preview.
                     ;; 3. Con: Slower npm install
                     {:filter (partial not-gitignored gitignore)})
    ;; Copy modules dir only if present, then sync modules in target project
    (let [new-ctx (kit/read-ctx (io/concat-path target-dir "kit.edn"))
          src-modules-root (modules/root ctx)
          tgt-modules-root (modules/root new-ctx)]
      (when (io/exists? src-modules-root)
        (println "Copying modules from" src-modules-root "to" tgt-modules-root)
        (io/clone-folder src-modules-root tgt-modules-root {:filter (partial not-gitignored (gitignore/default-rules))}))
      (println "Syncing modules in target project...")
      (modules/sync-modules! new-ctx))))

(defn preview-install
  "Previews the installation of a module by copying the current project files to a
   temporary directory and simulating the installation there. Overwrites project
   modules with modules specified in local-modules as if they were synced. These
   would typically contain modules under development, with changes that need to
   be tested."
  [preview-folder local-modules module-key kit-edn-path opts]
  (let [source-folder (io/parent-name kit-edn-path)]
    (io/mkdirs preview-folder)
    (copy-project-files source-folder preview-folder)
    (let [new-kit-edn-path (io/concat-path preview-folder "kit.edn")
          ctx              (kit/read-ctx new-kit-edn-path)]
      (doseq [{:keys [name path]} local-modules]
        (io/clone-folder path
                         (io/concat-path (modules/root ctx) name)))
      (kit/install-module module-key new-kit-edn-path opts))))

(defn- make-reporter [warning-log error-log]
  (letfn [(append-to-log [log-path msg data]
            (spit log-path (str msg " " (when data (pr-str data)) "\n") :append true))]
    (reify reporter/Reporter
      (warning [_ msg _data]
        (println "WARNING:" msg)
        (append-to-log warning-log msg nil))
      (error [_ msg data]
        (println "ERROR:" msg data)
        (append-to-log error-log msg data)))))

(defn- safe-slurp [path]
  (try
    (slurp path)
    (catch Exception _e
      "")))

(defn watch [project-root preview-folder module-repos module opts]
  (println "Registering to watch project at" project-root "for changes")
  (doseq [{:keys [path]} module-repos]
    (println "Registering to watch module repo at" path "for changes"))
  (let [project-gitignore (gitignore/load-gitignore (io/concat-path project-root ".gitignore") {:safe? true})
        diff-html-file    "kit-preview-diff.html"
        diff-html-path    (io/concat-path preview-folder diff-html-file)
        warning-log       (io/concat-path preview-folder "kit-preview-warnings.log")
        error-log         (io/concat-path preview-folder "kit-preview-errors.log")
        preview           #(let [reporter (make-reporter warning-log error-log)]
                             (reporter/with-reporter reporter
                               (try
                                 (println "Performing preview installation...")
                                 (preview-install preview-folder module-repos module (io/concat-path project-root "kit.edn") (merge opts
                                                                                                                                    {:reporter reporter}))
                                 (catch Exception e
                                   (reporter/report-error "Preview installation failed." {:exception e}))
                                 (finally
                                   (->> (diff/diff-folders project-root preview-folder)
                                        (remove (fn [patch] (= diff-html-file (:name patch))))
                                        (diff/write-diff-page diff-html-path (safe-slurp warning-log) (safe-slurp error-log)))
                                   (println "Open" diff-html-path "to see the preview diff.")))))
        change-ch         (async/chan 1)
        stop-ch           (async/chan)
        done-ch           (async/chan)
        watcher           (watcher/watch (fn on-event [path] (when #(not (gitignore/ignored? project-gitignore path))
                                                               (println "** changed" path)
                                                               (async/put! change-ch path)))
                                         (conj (map :path module-repos)
                                               project-root)
                                         {:watch-folder? (fn watch-folder? [path]
                                                           ;; TODO: It should actually load each .gitignore separately for project-root and each of the kit modules and
                                                           ;; use it here, based on whether the path is under project-root or under one of the module paths.
                                                           (println "** watch-folder?" path (not (gitignore/ignored? project-gitignore path)))
                                                           (not (gitignore/ignored? project-gitignore path)))})]
    (async/go-loop []
      (println "** Waiting for changes...")
      (async/alt!
        change-ch ([path]
                   (println "** Detected change at" path ", re-running preview...")
                   (while (async/poll! change-ch)) ;; drain channel
                   (println "** Changes settled, running preview installation...")
                   (preview)
                   (recur))
        stop-ch ([_]
                 (watcher/stop watcher)
                 (println "** Stopping preview watcher.")
                 (async/close! done-ch))))
    (preview)
    {:stop-ch stop-ch
     :done-ch done-ch}))

(defn stop [{:keys [stop-ch]}]
  (async/close! stop-ch))

(defn await [{:keys [done-ch]}]
  (async/<!! done-ch))

(defn run
  "Entry point for running preview from command line."
  [{:keys [project-root preview-folder module-repos module opts] :as args}]
  (println args)
  (await (watch project-root preview-folder module-repos module opts)))

(comment
  (preview-install "/tmp/preview"
                   [{:name "bilus-modules" :path "../../../kit-modules"}]
                   :kit/karma
                   "../../../kit-pocketbase-example/kit.en"
                   {:accept-hooks? true})
  (kit/install-module :kit/karma "../../../kit-pocketbase-example/kit.edn" {:accept-hooks? true})
  (def gitignore (gitignore/load-gitignore (io/concat-path "../../../kit-pocketbase-example/" ".gitignore") {:safe? false}))

  (gitignore/ignored? gitignore ".clj-kondo")
  (copy-project-files "../../../kit-pocketbase-example/" "/tmp/preview")
  (->> (diff/diff-folders "../../../kit-pocketbase-example" "/tmp/preview")
       (diff/write-diff-page "/tmp/preview/kit-preview-diff.html" "warnings" "errors"))

  (run {:preview-root     "../../../kit-pocketbase-example"
        :preview-folder   "/tmp/preview"
        :module-repos     [{:name "bilus-modules" :path "../../../kit-modules"}]
        :module           :kit/starter
        :opts             {:accept-hooks? true}})

  (def w (watch "../../../kit-pocketbase-example" "/tmp/preview"
                [{:path "../../../kit-modules"
                  :name "bilus-modules"}]
                :kit/starter
                {:accept-hooks? true}))
  (stop w)

;
  )
