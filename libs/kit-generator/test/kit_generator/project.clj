(ns kit-generator.project
  (:require
   [clojure.string :as str]
   [kit-generator.io :as io]
   [kit.api :as kit]))

(def default-project-root "test/resources/generated")

(defn module-installed? [module-key & {:keys [project-root] :or {project-root default-project-root}}]
  (when-let [install-log (io/read-edn-safe (str project-root "/modules/install-log.edn"))]
    (= :success (get install-log module-key))))

(defn prepare-project
  "Sets up a test project in `project-root` and returns the path to the kit.edn file.
   The project has already synced modules and kit.edn but is otherwise empty."
  [module-repo-path & {:keys [project-root] :or {project-root default-project-root}}]
  (let [project-modules (str project-root "/modules/")
        ctx             {:ns-name      "myapp"
                         :sanitized    "myapp"
                         :name         "myapp"
                         :project-root project-root
                         :modules      {:root         project-modules
                                        :repositories {:root (str project-root "/modules")
                                                       :url  "https://github.com/foo/bar/never/used"
                                                       :tag  "master"
                                                       :name "kit"}}}
        kit-edn-path    (str project-root "/kit.edn")]
    (io/delete-folder project-root)
    (io/clone-folder module-repo-path
                     project-modules
                     {:filter #(not (str/ends-with? % "install-log.edn"))})
    (io/write-edn ctx kit-edn-path)
    kit-edn-path))

(def read-ctx kit/read-ctx)
