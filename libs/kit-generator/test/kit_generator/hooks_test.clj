(ns kit-generator.hooks-test
  (:require
   [clojure.java.io :as jio]
   [clojure.test :refer [deftest is testing]]
   [kit-generator.io :refer [with-temp-dir]]
   [kit.generator.hooks :as hooks]))

(deftest test-post-install-hook
  (testing "post-install hook runs shell command in specified directory"
    (with-temp-dir [dir "kit-hooks-test"]
      (hooks/run-hooks :post-install
                       {:hooks {:post-install ["touch created.txt"]}}
                       {:dir dir})
      (is (.exists (jio/file dir "created.txt"))))))

(deftest test-post-install-hook-skipped
  (testing "post-install hook can be skipped via confirm"
    (with-temp-dir [dir "kit-hooks-test"]
      (hooks/run-hooks :post-install
                       {:hooks {:post-install ["touch created.txt"]}}
                       {:dir dir
                        :confirm (fn [_] false)})
      (is (not (.exists (jio/file dir "created.txt")))))))

(deftest test-hook-failure
  (testing "failed hook throws exception"
    (with-temp-dir [dir "kit-hooks-test"]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Hook command failed"
                            (hooks/run-hooks :post-install
                                             {:hooks {:post-install ["exit 1"]}}
                                             {:dir dir}))))))

(deftest test-unsupported-hook-type
  (testing "unsupported hook type throws exception"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unsupported hook type"
                          (hooks/run-hooks :unknown-hook {} {})))))
