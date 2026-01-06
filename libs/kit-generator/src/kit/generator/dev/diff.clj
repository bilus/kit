(ns kit.generator.dev.diff
  (:require
   [autochrome.components :as comp]
   [autochrome.diff :as diff]
   [autochrome.difflog :as difflog]
   [autochrome.page :as page]
   [autochrome.parse :as parse]
   [babashka.process :refer [sh]]
   [clojure.java.io :as jio]
   [clojure.set :as set]
   [clojure.string :as str]
   [com.climate.claypoole :as cp]
   [kit.generator.gitignore :as gitignore]
   [kit.generator.io :as io]
   [om.dom :as dom]))

(defn diff-clj
  [atext btext]
  (let [aroot (parse/parse-one atext)
        broot (parse/parse-one btext)
        diff (diff/diff-forms aroot [broot])
        #_aroot #_broot]
    [diff aroot broot]))

(defn difflog-clj
  [atext btext]
  (apply difflog/diff2 (diff-clj atext btext)))

(defn clojure-file? [path]
  (and
   (some? path)
   (or (str/ends-with? path ".clj")
       (str/ends-with? path ".cljs")
       (str/ends-with? path ".cljc")
       (str/ends-with? path ".edn"))))

(defn text-file? [path]
  (and (some? path)
       (or
        (str/ends-with? path ".txt")
        (str/ends-with? path ".json")
        (str/ends-with? path ".md")
        (str/ends-with? path ".css")
        (str/ends-with? path ".js")
        (str/ends-with? path ".html"))))

(defn diff-files
  [left-file right-file]
  (when (and (clojure-file? left-file) (clojure-file? right-file))
    (let [atext (slurp left-file)
          btext (slurp right-file)]
      {:left {:path left-file
              :text atext}
       :right {:path right-file
               :text btext}
       :diff (difflog-clj atext btext)})))

(defn source-view
  [binary? clojure? text & children]
  (cond
    binary?
    (dom/div {:style {:color "white"}} "[binary file]")

    clojure?
    (page/diff-pane "" {} (:contents (parse/parse text)))

    :else
    (let [lines (str/split-lines text)
          line-count (count lines)
          start-line 1]
      (dom/div
       {:className "code-card"
        #_:id      #_id}
       (dom/div {:className "code-card-heading"}
                (first children)
                (dom/div {:className "code-card-heading-extra"} (rest children)))
       (dom/div {:className "container"}
                (dom/pre {:className "gutter"}
                         (dom/code {:className "punctuation"}
                                   (comp/line-numbers {:start-line start-line
                                                       :lines      line-count})))
                (dom/div {:style {:width "1px"}})
                (dom/pre
                 {}
                 text))))))

(defn clojure-diff-view
  [linkbase {:keys [old-path old-text new-path new-text] :as patch}]
  (try
    (cond
      (nil? old-path)
      (page/one-file-diff linkbase new-path "R" (parse/parse new-text))

      (nil? new-path)
      (page/one-file-diff linkbase old-path "L"
                          (-> old-text parse/parse page/delete-everything))

      :else
      (page/two-file-diff
       linkbase
       {:path old-path :root (parse/parse old-text)}
       {:path new-path :root (parse/parse new-text)}))
    (catch java.lang.AssertionError e
      (throw (ex-info "Failed to generate clojure diff"
                      {:error ::clojure-diff
                       :patch patch}
                      e)))))

(defn raw-diff-view
  [linkbase {:keys [rawdiff binary? old-text new-text] :as patch}]
  (comp/panes {}
              (->> (for [line rawdiff]
                     (cond
                       (.startsWith line "+") (dom/span {:className "added"} line)
                       (.startsWith line "-") (dom/span {:className "deleted"} line)
                       :else line))
                   (interpose "\n")
                   (apply dom/pre {}))
              (source-view binary? false new-text)))

(defn patch-id
  [patch]
  (let [{:keys [old-path new-path]} patch]
    (page/md5sum (str old-path "?" new-path))))

(defn link-to-patch
  [patch & body]
  (apply dom/a {:href (str "#" (patch-id patch))} body))

(defn patch-anchor
  [patch]
  (dom/a {:className "anchor"
          :id        (patch-id patch)}))

(defn patch-heading-view
  [{:keys [old-path new-path name] :as patch}]
  (dom/div {:className "filename-heading"
            :style     {:display "flex"
                        :gap     "8px"}}
           (link-to-patch patch "🔗")
           (patch-anchor patch)
           (cond
             (nil? old-path) (str name " (new file)")
             (nil? new-path) (str name " (deleted)")
             :else name)))

(defn patch-action
  [{:keys [old-path new-path]}]
  (cond
    (and (nil? old-path) (some? new-path)) "create "
    (and (some? old-path) (nil? new-path)) "remove "
    :else "modify "))

(defn toc-view
  [patches]
  (dom/div {}
           (dom/h2 {:style {:color "#ffffff"}}
                   (cond
                     (zero? (count patches))
                     "No changes"

                     (= (count patches) 1)
                     "1 change:"

                     :else
                     (str (count patches) " change(s):")))
           (dom/ul {:style {:padding-left 0
                            :margin-bottom "40px"}}
                   (for  [[{:keys [name] :as patch} i] (map vector patches (range))]
                     (dom/li {:key i
                              :className "filename-heading"
                              :style {:background "black"
                                      :list-style "none"
                                      :margin-left 0
                                      :padding-left "4px"}}
                             (link-to-patch
                              patch (str (patch-action patch) name)))))))

(defn patch-view
  [linkbase {:keys [binary? old-path old-text new-path new-text rawdiff] :as patch}]
  (let [clojure? (or (clojure-file? new-path)
                     (clojure-file? old-path))]
    (cond
      (and (some? old-text) (nil? new-text))
      (comp/panes
       {}
       (dom/div {})
       (dom/div {}))

      (and (nil? old-text) (some? new-text))
      (comp/panes
       {}
       (dom/div {})
       (source-view binary? clojure? new-text))

      (and (nil? rawdiff)
           clojure?)
      (clojure-diff-view linkbase patch)

      :else
      (raw-diff-view linkbase patch))))

(defn error-page [title errors]
  (page/page title
             (dom/div {}
                      (dom/h2 {:style {:color "red"}} "Errors during preview generation:")
                      (dom/pre {} errors))))

(defn warning-view [warnings]
  (when (not-empty warnings)
    (dom/div {:style {:border "2px solid orange"
                      :padding "10px"
                      :margin-bottom "20px"}}
             (dom/h2 {:style {:color "orange"}} "Warnings during preview generation:")
             (dom/pre {:style {:color "orange"}}
                      warnings))))

(defn diff-page
  [linkbase title warning-log error-log patches]
  (println (count patches) "changed files")
  (if-let [errors (not-empty error-log)]
    (error-page title errors)
    (let [changed-files (->> patches
                             (sort-by (fn [{:keys [old-path new-path]}]
                                        (or old-path new-path)))
                            ;; Tag with index to preserve ordering.
                             (map-indexed (fn [i patch] (merge patch {:i i}))))]
      (->> changed-files
           (cp/upmap
            (cp/threadpool (cp/ncpus))
            (fn [{:keys [i] :as patch}]
              {:i i
               :dom [(patch-heading-view patch) (patch-view linkbase patch) (comp/spacer) (comp/spacer)]}))
           (sort-by :i)
           (map :dom)
           (apply concat)
           (apply dom/div {}
                  (warning-view warning-log)
                  (toc-view changed-files)
                  (dom/script {}
                              "window.onload = function() {
    if (window.location.hash) {
      document.querySelector(window.location.hash)?.scrollIntoView();
    }
  };"))

           (comp/root {})
           (page/page title)))))

(defn write-diff-page
  [path warning-log error-log patches]
  (spit path (diff-page "" "" warning-log error-log patches)))

(defn- raw-diff
  "Runs diff -u on two files. Use /dev/null to represent a missing file."
  [old-path  new-path]
  (let [res (sh {:out :string} "diff" "-u"
                (or old-path "/dev/null")
                (or  new-path "/dev/null"))]
    (->> res
         :out
         str/split-lines)))

(defn patch
  "Creates a patch for a file. old-dir or new-dir can be nil for added/deleted files."
  [old-dir new-dir path]
  (let [old-path (and old-dir (io/concat-path old-dir path))
        new-path (and new-dir (io/concat-path new-dir path))
        binary? (and (not (text-file? path))
                     (or
                      (and old-path (io/binary-file? old-path))
                      (and new-path (io/binary-file? new-path))))
        text? (not binary?)
        old-text (and old-path text? (slurp old-path))
        new-text (and new-path text? (slurp new-path))
        rawdiff (when (and (not binary?) (not (clojure-file? path))) (raw-diff old-path new-path))]
    (if (and (nil? old-path) (nil? new-path))
      (throw (ex-info "Internal error: both old path and new path are nil" {:error ::nil-patch-paths :old-dir old-dir}))
      {:name path
       :binary? binary?
       :old-path old-path
       :old-text old-text
       :new-path new-path
       :new-text new-text
       :rawdiff rawdiff})))

(defn- list-files
  "Returns a set of relative paths for all files in dir, excluding gitignored files."
  [dir gitignore]
  (->> (file-seq (jio/file dir))
       (filter #(.isFile %))
       (map #(io/relative-path % dir))
       (remove #(gitignore/ignored? gitignore %))
       set))

(defn diff-folders
  "Compares two directories and returns a list of patches for files that differ.
   Uses gitignore rules from old-dir to filter files and compares modification timestamps."
  [old-dir new-dir]
  (assert (not (str/blank? old-dir)) "old-dir must be provided")
  (assert (not (str/blank? new-dir)) "new-dir must be provided")
  (let [gitignore (gitignore/load-gitignore (io/concat-path old-dir ".gitignore") :safe? true)
        old-files (list-files old-dir gitignore)
        new-files (list-files new-dir gitignore)
        added-files (set/difference new-files old-files)
        deleted-files (set/difference old-files new-files)
        common-files (set/intersection old-files new-files)
        changed-files (filter (fn [path]
                                (let [old-path (io/concat-path old-dir path)
                                      new-path (io/concat-path new-dir path)]
                                  (not= (io/last-modified old-path)
                                        (io/last-modified new-path))))
                              common-files)]
    (->> (concat
          (map #(patch nil new-dir %) added-files)
          (map #(patch old-dir nil %) deleted-files)
          (map #(patch old-dir new-dir %) changed-files)))))

(comment

  (diff-clj
   (slurp "/Users/marcin.bilski/dev/bilus/kit-starter/kit-pocketbase-example/src/clj/bilus/kit_pocketbase_example/core.clj")
   (slurp "/tmp/preview/src/clj/bilus/kit_pocketbase_example/core.clj"))

  (def aroot (parse/parse (slurp "/Users/marcin.bilski/dev/bilus/kit-starter/kit-pocketbase-example/src/clj/bilus/kit_pocketbase_example/core.clj")))
  (def broot (parse/parse (slurp "/tmp/preview/src/clj/bilus/kit_pocketbase_example/core.clj")))
  ("/tmp/diff.html"
   (diff-files
    "/Users/marcin.bilski/dev/bilus/kit-starter/kit-pocketbase-example/src/clj/bilus/kit_pocketbase_example/core.clj"
    "/tmp/preview/src/clj/bilus/kit_pocketbase_example/core.clj"))

  (require '[babashka.process :refer [sh]])

  (println (:out (sh {:out :string} "diff" "-u"
                     "/Users/marcin.bilski/dev/bilus/kit-starter/kit-pocketbase-example/src/clj/bilus/kit_pocketbase_example/core.clj"
                     "/tmp/preview/src/clj/bilus/kit_pocketbase_example/core.clj")))
  (let [old-dir "/Users/marcin.bilski/dev/bilus/kit-starter/kit-pocketbase-example"
        new-dir "/tmp/preview"
        paths ["src/clj/bilus/kit_pocketbase_example/core.clj"
               "src/clj/bilus/kit_pocketbase_example/config.clj"
               "README.md"]
        patches (map #(patch old-dir new-dir %) paths)]
    (println patches)
    (write-diff-page "/tmp/out.html" patches))
                                        ;
  )
