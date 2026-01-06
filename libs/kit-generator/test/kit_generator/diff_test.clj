(ns kit-generator.diff-test
  (:require
   [clojure.java.io :as jio]
   [clojure.test :refer [deftest is testing]]
   [kit-generator.io :refer [clone-folder concat-path create-files with-temp-dir]]
   [kit.generator.diff :as diff]))

(deftest test-diff-folders-unchanged
  (testing "identical folders return no patches"
    (with-temp-dir [old-dir "old"]
      (with-temp-dir [new-dir "new"]
        (let [old-files {"src/core.clj" "(ns core)"
                         "README.md" "# Hello"}]
          (create-files old-dir old-files)
          (clone-folder old-dir new-dir)
          (is (empty? (diff/diff-folders old-dir new-dir))))))))

(deftest test-diff-folders-added-file
  (testing "detects added files"
    (with-temp-dir [old-dir "old"]
      (with-temp-dir [new-dir "new"]
        (let [old-files {"src/core.clj" "(ns core)"}
              added-files {"src/new.clj" "(ns new)"}]
          (create-files old-dir old-files)
          (clone-folder old-dir new-dir)
          (create-files new-dir added-files)
          (let [patches (diff/diff-folders old-dir new-dir)]
            (is (= 1 (count patches)))
            (let [patch (first patches)]
              (is (nil? (:old-path patch)))
              (is (some? (:new-path patch)))
              (is (= "(ns new)" (:new-text patch))))))))))

(deftest test-diff-folders-deleted-file
  (testing "detects deleted files"
    (with-temp-dir [old-dir "old"]
      (with-temp-dir [new-dir "new"]
        (let [old-files {"src/core.clj" "(ns core)"
                         "src/old.clj" "(ns old)"}]
          (create-files old-dir old-files)
          (clone-folder old-dir new-dir)
          (jio/delete-file (concat-path new-dir "src/old.clj"))
          (let [patches (diff/diff-folders old-dir new-dir)]
            (is (= 1 (count patches)))
            (let [patch (first patches)]
              (is (some? (:old-path patch)))
              (is (nil? (:new-path patch)))
              (is (= "(ns old)" (:old-text patch))))))))))

(deftest test-diff-folders-modified-file
  (testing "detects modified files by timestamp"
    (with-temp-dir [old-dir "old"]
      (with-temp-dir [new-dir "new"]
        (let [old-files {"src/core.clj" "(ns core)"}
              modified-files {"src/core.clj" "(ns core :changed)"}]
          (create-files old-dir old-files)
          (clone-folder old-dir new-dir)
          (Thread/sleep 1) ; ensure different timestamps
          (create-files new-dir modified-files)
          (let [patches (diff/diff-folders old-dir new-dir)]
            (is (= 1 (count patches)))
            (let [patch (first patches)]
              (is (some? (:old-path patch)))
              (is (some? (:new-path patch)))
              (is (= "(ns core)" (:old-text patch)))
              (is (= "(ns core :changed)" (:new-text patch))))))))))

(deftest test-diff-folders-ignores-gitignored
  (testing "ignores files matching .gitignore patterns"
    (with-temp-dir [old-dir "old"]
      (with-temp-dir [new-dir "new"]
        (let [old-files {"src/core.clj" "(ns core)"
                         ".gitignore" "target/\n*.log"
                         "target/classes/foo.class" "binary"
                         "app.log" "log content"}
              modified-files {"target/classes/foo.class" "binary-changed"
                              "app.log" "log content changed"}]
          (create-files old-dir old-files)
          (clone-folder old-dir new-dir)
          (Thread/sleep 1)
          (create-files new-dir modified-files)
          (let [patches (diff/diff-folders old-dir new-dir)]
            ;; Should not include target/ or *.log files
            (is (empty? patches))))))))
