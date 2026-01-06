(ns kit.generator.reporter)

(def ^:dynamic *reporter* nil)

(defprotocol Reporter
  (warning [this msg data])
  (error [this msg data]))

(defmacro with-reporter [reporter & body]
  `(binding [*reporter* ^Reporter ~reporter]
     ~@body))

(defn report-warning [msg data]
  (when *reporter*
    (warning *reporter* msg data)))

(defn report-error [msg data]
  (when *reporter*
    (error *reporter* msg data)))

(comment
  (with-reporter
    (reify Reporter
      (warning [_this msg data]
        (println "WARNING:" msg data))
      (error [_this msg data]
        (println "ERROR:" msg data)))
    (report-warning "This is a warning" {:code 123})
    (report-error "This is an error" {:code 456})))
