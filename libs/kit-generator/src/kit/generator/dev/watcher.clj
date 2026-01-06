(ns kit.generator.dev.watcher
  "Directory watcher with support for excluding directories via predicate."
  (:import
   [io.methvin.watcher
    DirectoryChangeEvent
    DirectoryChangeEvent$EventType
    DirectoryChangeListener
    DirectoryWatcher]
   [io.methvin.watcher.visitor FileTreeVisitor]
   [java.nio.file
    FileVisitResult
    Files
    Paths
    SimpleFileVisitor]
   [java.util.concurrent TimeUnit TimeoutException]))

(defn- filtering-visitor
  "FileTreeVisitor that only walks directories matching the predicate."
  [include-dir?]
  (reify FileTreeVisitor
    (recursiveVisitFiles [_ root on-directory on-file]
      (Files/walkFileTree root
                          (proxy [SimpleFileVisitor] []
                            (preVisitDirectory [dir _attrs]
                              (if (include-dir? (str dir))
                                (do (.call on-directory dir)
                                    FileVisitResult/CONTINUE)
                                FileVisitResult/SKIP_SUBTREE))
                            (visitFile [file _attrs]
                              (.call on-file file)
                              FileVisitResult/CONTINUE)
                            (visitFileFailed [_file _exc]
                              FileVisitResult/CONTINUE)
                            (postVisitDirectory [_dir _exc]
                              FileVisitResult/CONTINUE))))))

(defn- to-path [s]
  (Paths/get (str s) (into-array String [])))

(defn- event->map [^DirectoryChangeEvent e]
  (let [event-type (.eventType e)]
    {:type (condp = event-type
             DirectoryChangeEvent$EventType/CREATE :create
             DirectoryChangeEvent$EventType/MODIFY :modify
             DirectoryChangeEvent$EventType/DELETE :delete
             DirectoryChangeEvent$EventType/OVERFLOW :overflow)
     :path (str (.path e))}))

(deftype Watcher [^DirectoryWatcher watcher future]
  clojure.lang.IDeref
  (deref [_]
    (deref future))

  clojure.lang.IBlockingDeref
  (deref [_ timeout-ms timeout-val]
    (try
      (.get future timeout-ms TimeUnit/MILLISECONDS)
      (catch TimeoutException _
        timeout-val)))

  java.io.Closeable
  (close [_]
    (.close watcher)))

(defn watch
  "Watches directories for changes, with support for filtering directories.

   Options:
     :watch-folder?  - predicate called with directory path string, return true to include

   Returns a watcher object that can be:
     - deref'd to block until the watcher stops (like a future)
     - stopped via (stop watcher)"
  [on-event paths & {:keys [watch-folder?] :or {watch-folder? (fn [_] true)}}]
  (println "** paths to watch:" paths)
  (let [builder (-> (DirectoryWatcher/builder)
                    (.paths (map to-path paths))
                    (.listener (reify DirectoryChangeListener
                                 (onEvent [_ e]
                                   (on-event (event->map e))))))
        _       (when watch-folder?
                  (.fileTreeVisitor builder (filtering-visitor watch-folder?)))
        watcher (.build builder)
        fut     (.watchAsync watcher)]
    (->Watcher watcher fut)))

(defn stop
  "Stops the watcher."
  [^Watcher watcher]
  (.close watcher))

(comment
  (def w (watch prn ["/tmp/preview"]
                {:watch-folder? (fn [path]
                                  (println "** watch-folder?" path)
                                  (not (re-find #"(^|/)(\.git|node_modules|target|.clj-kondo|.lsp)(/|$)" path)))}))
  (stop w)

  (def w (watch prn ["/tmp/preview"]))
  (println "foo")
  (future
    @w
    (println "watcher stopped"))
  (stop w)
  5
  ;
  )
