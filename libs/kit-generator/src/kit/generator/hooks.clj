(ns kit.generator.hooks
  "Execute script hooks defined in module configuration."
  (:require
   [babashka.process :refer [sh]]))

(defmulti run-hooks (fn [hook _ _] hook))

(defmethod run-hooks :post-install
  [hook module-config {:keys [confirm dir] :or {confirm (fn [_] true)}}]
  (println "** dir" dir)
  (when-let [actions (seq (get-in module-config [:hooks hook]))]
    (if (confirm actions)
      (doseq [action actions]
        (println "$" action)
        (let [{:keys [exit out]} (sh {:continue true
                                      :dir      dir
                                      :out      :string
                                      :err      :out} "sh" "-c" action)]
          (println out)
          (when (not (zero? exit))
            (throw (ex-info (str "Hook command failed: " action)
                            {:error  ::hook-failed
                             :action action
                             :exit   exit
                             :dir    dir
                             :out    out})))))
      (println "Skipping hooks for" hook))))

(defmethod run-hooks :default
  [hook _ _]
  (throw (ex-info (str "Unsupported hook type: " hook) {:hook hook})))

(defn describe-hooks
  "A sequence of strings describing the hooks defined by the module."
  [{:module/keys [resolved-config]}]
  (->> resolved-config
       :hooks
       keys
       (map #(str "run " (name %) " hook"))))
