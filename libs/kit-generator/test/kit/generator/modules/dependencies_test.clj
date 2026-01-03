(ns kit.generator.modules.dependencies-test
  (:require
   [clojure.test :refer [are deftest is testing]]
   [matcher-combinators.test]
   [kit-generator.project :refer [prepare-project read-ctx]]
   [kit.generator.modules.dependencies :as deps]))

(def module-repo-path "test/resources/modules")

(defn ctx []
  (read-ctx (prepare-project module-repo-path)))

(deftest resolve-requires
  (testing "empty requires"
    (is (= #{} (deps/resolve-dependencies {:default {}} :default))))
  (testing "simple default requires"
    (is (= #{:a} (deps/resolve-dependencies {:default {:requires [:a]}} :default)))))

(deftest resolve-feature-requires
  (testing "simple feature requires"
    (is (= #{:b} (deps/resolve-dependencies {:default {:feature-requires [:base]}
                                             :base {:requires [:b]}} :default))))
  (testing "double feature require"
    (is (= #{:a :b} (deps/resolve-dependencies {:default {:feature-requires [:base :tool]}
                                                :base {:requires [:a]}
                                                :tool {:requires [:b]}} :default))))
  (testing "feature requires another feature"
    (is (= #{:a :b} (deps/resolve-dependencies {:default {:feature-requires [:base :tool]}
                                                :base {:requires [:a]}
                                                :tool {:requires [:b] :feature-requires [:base]}} :default))))
  (testing "ciclic feature require"
    (is (= #{:a :b} (deps/resolve-dependencies {:default {:feature-requires [:base :tool]}
                                                :base {:requires [:a] :feature-requires [:tool]}
                                                :tool {:requires [:b] :feature-requires [:base]}} :default)))))

(deftest dependency-tree
  (testing "building dependency tree"
    (are [module-key opts expected-tree] (match? expected-tree (deps/dependency-tree (ctx) module-key opts))
      :meta {}                      {:module/key          :meta
                                     :module/config       map?
                                     :module/opts         {}
                                     :module/dependencies []}
      ;; :meta {:feature-flag :extras} {:module/key          :meta
      ;;                                :module/config       {:default {}
      ;;                                                      :extras  {:requires [:db]}}
      ;;                                :module/opts         {:feature-flag :extras}
      ;;                                :module/dependencies [{:module/key          :db
      ;;                                                       :module/config       {:default {}}
      ;;                                                       :module/opts         {}
      ;;                                                       :module/dependencies []}]}
      ;; :meta {:feature-flag :full}   {:module/key          :meta
      ;;                                :module/config       {:default {}
      ;;                                                      :extras  {:requires [:db]}
      ;;                                                      :full    {:requires         [:html]
      ;;                                                                :feature-requires [:extras]}}
      ;;                                :module/opts         {:feature-flag :full}
      ;;                                :module/dependencies [{:module/key          :html
      ;;                                                       :module/config       {:default {}}
      ;;                                                       :module/opts         {}
      ;;                                                       :module/dependencies []}
      ;;                                                      {:module/key          :db
      ;;                                                       :module/config       {:default {}}
      ;;                                                       :module/opts         {}
      ;;                                                       :module/dependencies []}]}

;
      )))
