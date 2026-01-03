(ns kit.generator.modules.dependencies
  (:require
   [kit.generator.modules :as modules]
   [kit.generator.modules.generator :as generator]))

(defn resolve-dependencies
  ([module-config feature-flag]
   (resolve-dependencies module-config feature-flag #{}))
  ([module-config feature-flag reqs]
   (let [requires (get-in module-config [feature-flag :requires] [])
         feature-requires (get-in module-config [feature-flag :feature-requires])]
     (if feature-requires
       (into #{} (mapcat #(resolve-dependencies (dissoc module-config feature-flag) % requires) feature-requires))
       (into reqs requires)))))

(defn dependency-tree*
  [{:keys [modules] :as ctx} module-key opts]
  (if (modules/module-exists? ctx module-key)
    (let [{:keys [module-config]} (generator/read-module-config ctx modules module-key)
          {:keys [feature-flag] :or {feature-flag :default} :as module-opts} (get opts module-key {})
          deps (resolve-dependencies module-config feature-flag)]
      {:module/key module-key
       :module/config module-config
       :module/opts module-opts
       :module/dependencies (mapv #(dependency-tree* ctx % opts) deps)})
    (throw (ex-info (str "Module " module-key " not found.") {:module-key module-key}))))

(defn dependency-tree
  "A tree of module configs and their dependencies.
   > NOTE: opts must be flat options. See kit.api/flat-module-options for more details."
  [ctx module-key opts]
  (-> ctx
      (modules/load-modules)
      (dependency-tree* module-key opts)))
