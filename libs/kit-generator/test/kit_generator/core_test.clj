(ns kit-generator.core-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is are]]
   [kit-generator.injections]
   [kit-generator.io :as io]
   [kit-generator.project :refer [project-root module-installed? prepare-project]]
   [kit.api :as kit]))

(declare match?)

(def module-repo-path "test/resources/modules")

(defn test-install-module*
  [module-key opts expected-files]
  (let [kit-edn-path (prepare-project module-repo-path)]
    (is (not (module-installed? module-key)))
    (is (= :done (kit/install-module module-key kit-edn-path opts)))
    (is (module-installed? module-key))
    (is (empty? (io/folder-mismatches project-root
                                      expected-files
                                      {:filter #(not (str/starts-with? % "modules/"))})))))

(deftest test-install-module
  (are [module-key opts expected-files] (test-install-module* module-key opts expected-files)
    :meta {}                                {"resources/public/css/app.css"        []
                                             "kit.edn"                             []}
    :meta {:feature-flag :extras}           {"resources/public/css/styles.css"     []
                                             "src/clj/myapp/db.clj"                []
                                             "kit.edn"                             []}
    :meta {:feature-flag :full}             {"resources/public/css/app.css"        []
                                             "resources/public/css/styles.css"     []
                                             "src/clj/myapp/db.clj"                []
                                             "kit.edn"                             []}
    :meta {:feature-flag :extras
           :db {:feature-flag :postgres}}   {"resources/public/css/styles.css"     []
                                             "src/clj/myapp/db.clj"                []
                                             "src/clj/myapp/db/postgres.clj"       []
                                             "kit.edn"                             []}
    :meta {:feature-flag :extras
           :db {:feature-flag :migrations}} {"resources/public/css/styles.css"     []
                                             "src/clj/myapp/db.clj"                []
                                             "src/clj/myapp/db/postgres.clj"       []
                                             "src/clj/myapp/db/migratus.clj"       []
                                             "src/clj/myapp/db/migrations/001.clj" []
                                             "kit.edn"                             []}
    :meta {:accept-hooks? true
           :feature-flag :with-hooks}       {"post-install.txt"                    []
                                             "kit.edn"                             []}

;;
    ))

;; TODO: Should feature-requires be transient? If so, add tests for that.

(defn test-dependency-tree*
  [module-key opts expected-tree]
  (let [kit-edn-path (prepare-project module-repo-path)
        tree (kit/dependency-tree module-key kit-edn-path opts)]
    (is (match? expected-tree tree))))

(deftest test-dependency-tree
  (are [module-key opts expected-tree] (test-dependency-tree* module-key opts expected-tree)
    :meta {}                                {:module/key          :meta
                                             :module/config       map?
                                             :module/opts         {}
                                             :module/dependencies []}
    :meta {:feature-flag :extras}           {:module/key          :meta
                                             :module/config       map?
                                             :module/opts         {:feature-flag :extras}
                                             :module/dependencies [{:module/key          :db
                                                                    :module/config       map?
                                                                    :module/opts         {}
                                                                    :module/dependencies []}]}
    :meta {:feature-flag :full}             {:module/key           :meta
                                             :module/config        map?
                                             :module/opts          {:feature-flag :full}
                                             :module/dependencies  [{:module/key          :db
                                                                     :module/config       map?
                                                                     :module/opts         {}
                                                                     :module/dependencies []}]}
    :meta {:feature-flag :full
           :db {:feature-flag :migrations}} {:module/key           :meta
                                             :module/config        map?
                                             :module/opts          {:feature-flag :full}
                                             :module/dependencies  [{:module/key          :db
                                                                     :module/config       map?
                                                                     :module/opts         {:feature-flag :migrations}
                                                                     :module/dependencies [{:module/key          :migratus
                                                                                            :module/config       map?
                                                                                            :module/opts         {}
                                                                                            :module/dependencies []}]}]}
;
    ))
(comment
  (clojure.test/run-tests 'kit-generator.core-test)
  (kit/print-dependencies :meta (prepare-project module-repo-path) {:feature-flag :full
                                                                    :db          {:feature-flag :migrations}}))
