(ns ctia.test-helpers.es
  "ES test helpers"
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.test :refer [testing]]
            [ctia.stores.es.init :as es-init]
            [ctia.stores.es.schemas :refer [ESConnServices]]
            [ctia.stores.es.store :as es-store]
            [ctia.test-helpers.core :as h]
            [ductile.conn :as es-conn]
            [ductile.document :as es-doc]
            [ductile.index :as es-index]
            [puppetlabs.trapperkeeper.app :as app]
            [schema.core :as s]))

(s/defn app->ESConnServices
  :- ESConnServices
  "Create a ESConnServices map with an app"
  [app]
  (let [get-in-config (h/current-get-in-config-fn app)]
    {:ConfigService {:get-in-config get-in-config}
     :FeaturesService (-> (h/get-service-map app :FeaturesService)
                          (select-keys [:entity-enabled? :flag-value]))}))

(s/defn ->ESConnServices
  :- ESConnServices
  "Create a ESConnServices map without an app"
  []
  (let [get-in-config (h/build-get-in-config-fn)]
    {:ConfigService {:get-in-config get-in-config}}))

(defn delete-store-indexes
  ([restore-conn?]
   (let [app (h/get-current-app)
         {:keys [get-in-config]} (h/get-service-map app :ConfigService)
         {:keys [all-stores]} (h/get-service-map app :StoreService)]
     (delete-store-indexes
       restore-conn?
       all-stores
       get-in-config)))
  ([restore-conn? all-stores get-in-config]
   (doseq [store-impls (vals (all-stores))
           {:keys [state]} store-impls]
     (es-store/delete-state-indexes state)
     (when restore-conn?
       (es-init/init-es-conn!
         (es-init/get-store-properties (get-in state [:props :entity])
                                       get-in-config)
         (app->ESConnServices (h/get-current-app)))))))

(defn ^{:deprecated "1.1"
        :superseded-by "ctia.test-helpers.core/stop-and-cleanup"}
  fixture-delete-store-indexes
  "walk through all the es stores delete each store indexes"
  [t]
  (let [app (h/get-current-app)
        {:keys [get-in-config]} (h/get-service-map app :ConfigService)
        {:keys [all-stores]} (h/get-service-map app :StoreService)]
    (delete-store-indexes true all-stores get-in-config)
    (try
      (t)
      (finally
        (delete-store-indexes false all-stores get-in-config)))))

(s/defn purge-index-and-template [entity
                                  {{:keys [get-in-config]} :ConfigService
                                   :as services} :- ESConnServices]
  (let [{:keys [conn index]} (es-init/init-store-conn
                              (es-init/get-store-properties entity get-in-config)
                              services)
        index-wildcard (str index "*")]
    (when conn
        (es-index/delete! conn index-wildcard)
        (es-index/delete-template! conn index-wildcard))))

(defn fixture-purge-event-indices-and-templates
  "walk through all producers and delete their indices and templates"
  [t]
  (let [app (h/get-current-app)
        services (app->ESConnServices app)]
    (purge-index-and-template :event services)
    (try
      (t)
      (finally
        (purge-index-and-template :event services)))))

(defn purge-indices-and-templates [all-stores services]
  (doseq [entity (keys (all-stores))]
    (purge-index-and-template entity services)))

(def basic-auth
  {:type :basic-auth
   :params {:user "elastic" :pwd "ductile"}})

(def basic-auth-properties
  (into ["ctia.store.es.default.auth.type" (:type basic-auth)]
        (mapcat #(list (str "ctia.store.es.default.auth.params." (-> % key name))
                       (val %)))
        (:params basic-auth)))

(defn -es-port []
  "9207")

(defn -es-version []
  7)

(defn fixture-properties:es-store [t]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties (into basic-auth-properties
                           ["ctia.store.es.default.shards" 5
                            "ctia.store.es.default.replicas" 1
                            "ctia.store.es.default.refresh" "true"
                            "ctia.store.es.default.refresh_interval" "1s"
                            "ctia.store.es.default.port" (-es-port)
                            "ctia.store.es.default.indexname" "test_ctia"
                            "ctia.store.es.default.default_operator" "AND"
                            "ctia.store.es.default.aliased" true
                            "ctia.store.es.default.rollover.max_docs" 50
                            "ctia.store.es.default.version" (-es-version)
                            "ctia.store.es.default.default-sort" "timestamp,created,id"
                            "ctia.store.es.event.default-sort" "timestamp,id"
                            "ctia.store.es.relationship.default-sort" "created,id"
                            "ctia.store.es.actor.indexname" "ctia_actor"
                            "ctia.store.es.actor.default_operator" "OR"
                            "ctia.store.es.asset.indexname" "ctia_assets"
                            "ctia.store.es.asset-mapping.indexname" "ctia_asset_mapping"
                            "ctia.store.es.asset-properties.indexname" "ctia_asset_properties"
                            "ctia.store.es.attack-pattern.indexname" "ctia_attack_pattern"
                            "ctia.store.es.campaign.indexname" "ctia_campaign"
                            "ctia.store.es.coa.indexname" "ctia_coa"
                            "ctia.store.es.event.indexname" "ctia_event"
                            "ctia.store.es.data-table.indexname" "ctia_data-table"
                            "ctia.store.es.feedback.indexname" "ctia_feedback"
                            "ctia.store.es.identity.indexname" "ctia_identities"
                            "ctia.store.es.incident.indexname" "ctia_incident"
                            "ctia.store.es.indicator.indexname" "ctia_indicator"
                            "ctia.store.es.investigation.indexname" "ctia_investigation"
                            "ctia.store.es.judgement.indexname" "ctia_judgement"
                            "ctia.store.es.malware.indexname" "ctia_malware"
                            "ctia.store.es.relationship.indexname" "ctia_relationship"
                            "ctia.store.es.casebook.indexname" "ctia_casebook"
                            "ctia.store.es.sighting.indexname" "ctia_sighting"
                            "ctia.store.es.identity-assertion.indexname" "ctia_identity_assertion"
                            "ctia.store.es.target-record.indexname" "ctia_target_record"
                            "ctia.store.es.tool.indexname" "ctia_tool"
                            "ctia.store.es.vulnerability.indexname" "ctia_vulnerability"
                            "ctia.store.es.weakness.indexname" "ctia_weakness"

                            "ctia.store.actor" "es"
                            "ctia.store.asset" "es"
                            "ctia.store.asset-mapping" "es"
                            "ctia.store.asset-properties" "es"
                            "ctia.store.attack-pattern" "es"
                            "ctia.store.campaign" "es"
                            "ctia.store.coa" "es"
                            "ctia.store.data-table" "es"
                            "ctia.store.event" "es"
                            "ctia.store.feed" "es"
                            "ctia.store.feedback" "es"
                            "ctia.store.identity" "es"
                            "ctia.store.incident" "es"
                            "ctia.store.indicator" "es"
                            "ctia.store.investigation" "es"
                            "ctia.store.judgement" "es"
                            "ctia.store.malware" "es"
                            "ctia.store.relationship" "es"
                            "ctia.store.casebook" "es"
                            "ctia.store.sighting" "es"
                            "ctia.store.identity-assertion" "es"
                            "ctia.store.target-record" "es"
                            "ctia.store.tool" "es"
                            "ctia.store.vulnerability" "es"
                            "ctia.store.weakness" "es"
                            "ctia.store.bulk-refresh" "true"

                            ;; "ctia.migration.store.es.default.port" "9207"
                            "ctia.migration.store.es.migration.indexname" "ctia_migration"
                            "ctia.migration.store.es.default.rollover.max_docs" 50
                            "ctia.migration.store.es.event.rollover.max_docs" 1000])
    (t)))

(defn fixture-properties:es-hook [t]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.hook.es.enabled" true
                      "ctia.hook.es.port" (-es-port)
                      "ctia.hook.es.indexname" "test_ctia_events"]
    (t)))

(defn fixture-properties:es-hook:aliased-index [t]
  ;; Note: These properties may be overwritten by ENV variables
  (h/with-properties ["ctia.hook.es.enabled" true
                      "ctia.hook.es.port" (-es-port)
                      "ctia.hook.es.indexname" "test_ctia_events"
                      "ctia.hook.es.slicing.strategy" "aliased-index"
                      "ctia.hook.es.slicing.granularity" "week"]
    (t)))

(defn str->doc
  [str-doc]
  (json/parse-string str-doc true))

(defn get-indexname [app entity]
  {:pre [(simple-keyword? entity)]
   :post [(string? %)]}
  (let [{{:keys [get-in-config]} :ConfigService} (app/service-graph app)
        indexname (get-in-config [:ctia :store :es entity :indexname])]
    (when-not indexname
      (throw (ex-info (str "No indexname for " entity) {})))
    indexname))

(defn get-migration-indexname [app entity]
  {:pre [(simple-keyword? entity)]
   :post [(string? %)]}
  (let [{{:keys [get-in-config]} :ConfigService} (app/service-graph app)
        indexname (get-in-config [:ctia :migration :store :es entity :indexname])]
    (when-not indexname
      (throw (ex-info (str "No migration indexname for " entity) {})))
    indexname))

(defn prepare-bulk-ops
  [app str-doc]
  (let [{:keys [_id _index _source]} (str->doc str-doc)]
    (assoc _source
           :_index (get-indexname app (keyword (:type _source)))
           :_id _id)))

(defn load-bulk
  ([conn docs] (load-bulk conn docs "wait_for"))
  ([{:keys [version] :as conn} docs refresh?]
   (es-doc/bulk-index-docs conn
                           docs
                           {:refresh refresh?})))

(defn load-file-bulk
  [app es-conn filepath]
  (with-open [rdr (io/reader filepath)]
    (load-bulk es-conn
               (map (partial prepare-bulk-ops app)
                    (line-seq rdr)))))

(defn get-cat-indices [conn]
  (->> (es-index/cat-indices conn)
       (map (fn [{:keys [index] docs_count :docs.count}]
              {(keyword index) docs_count}))
       (into {})))

(defn get-cat-indices-clean
  "GET /_cat/indices removing system indices"
  [conn]
  (->> (get-cat-indices conn)
       (remove (fn [[k _]]
                 (string/starts-with? (name k) ".")))))

(defn -filter-activated-es-versions [versions]
  (filter (h/set-of-es-versions-to-test) versions))

(defmacro for-each-es-version
  "for each given ES version:
  - init an ES connection assuming that ES version n listens on port 9200 + n
  - expose anaphoric `version`, `es-port` and `conn` to use in body
  - wrap body with a `testing` block with with `msg` formatted with `version`
  - call `clean` fn if not `nil` before and after body (takes conn as parameter)."
  {:style/indent 2}
  [msg versions clean & body]
  `(let [;; avoid version and the other explicitly bound locals will to be captured
         clean-fn# ~clean
         msg# ~msg]
     (doseq [version# (-filter-activated-es-versions ~versions)
             :let [es-port# (+ 9200 version#)]]
       (h/with-properties
         (into ["ctia.store.es.default.host" "127.0.0.1"
                "ctia.store.es.default.port" es-port#
                "ctia.store.es.default.version" version#]
               basic-auth-properties)
         (let [conn# (es-conn/connect (es-init/get-store-properties ::no-store (h/build-get-in-config-fn)))]
           (try
             (testing (format "%s (ES version: %s).\n" msg# version#)
               (when clean-fn#
                 (clean-fn# conn#))
               (let [~'conn conn#
                     ~'version version#
                     ~'es-port es-port#]
                 ~@body))
             (finally
               (when clean-fn#
                 (clean-fn# conn#))
               (es-conn/close conn#))))))))

(defn build-mappings
  [base-mappings]
  {:properties base-mappings})
