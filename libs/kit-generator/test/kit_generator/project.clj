(ns kit-generator.project
  (:require
   [clojure.string :as str]
   [kit-generator.io :as io]
   [kit.api :as kit]
   [kit.generator.modules :as modules]))

(def sample-kit-edn-path "test/resources/kit.edn")
(def sample-module-repos "test/resources/modules")
(def default-project-root "test/resources/generated")

(defn module-installed? [module-key & {:keys [project-root] :or {project-root default-project-root}}]
  (when-let [install-log (io/read-edn-safe (str project-root "/modules/install-log.edn"))]
    (= :success (get install-log module-key))))

(defn prepare-project
  "Sets up a test project in `project-root` and returns the path to the kit.edn file.
   The project has already synced modules and kit.edn but is otherwise empty."
  [& {:keys [project-root] :or {project-root default-project-root}}]
  (let [kit-edn-path    (io/concat-path project-root "kit.edn")]
    (io/delete-folder project-root)
    (io/clone-folder sample-module-repos
                     (io/concat-path project-root "modules")
                     {:filter #(not (str/ends-with? % "install-log.edn"))})
    (io/clone-file sample-kit-edn-path kit-edn-path)
    ;; (io/write-edn ctx kit-edn-path)
    kit-edn-path))

(def read-ctx kit/read-ctx)
