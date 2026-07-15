(ns transport-equipment-repair.store
  "SSoT for the transport-equipment-repair-coordination actor, behind a
  `Store` protocol so the backend is a swap, not a rewrite -- the same
  seam every prior `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/transport_equipment_repair/store_contract_test.clj), which is the
  whole point: the actor, the Repair Governor and the audit ledger never
  know which SSoT they run on.

  `DatomicStore` uses `langchain-store.core` (ADR-2607141600) for the
  EDN-blob codec, `:db.unique/identity` schema and the seq-keyed
  event-log read/append pattern, instead of hand-rolling `enc`/`dec*`.

  This actor has FOUR coordination-proposal ops, each with its OWN
  append-only history collection and jurisdiction-scoped sequence
  counter: `:log-repair-record` (repair-record-log), `:schedule-repair-
  operation` (schedule-proposal), `:flag-safety-concern` (safety-concern-
  flag) and `:coordinate-return-to-service` (return-to-service-
  coordination-proposal). Every op may recur any number of times for the
  same asset (a ship, aircraft or railway rolling-stock unit gets logged
  repeatedly over its repair lifecycle, may be rescheduled, may
  accumulate multiple safety-concern flags, may have multiple return-
  to-service coordination rounds) BECAUSE this actor never actually
  operates repair equipment and never signs off on an airworthiness/
  seaworthiness/rail-safety-certification-authority decision -- it only
  ever proposes, logs and schedules (`:effect :propose` unconditionally,
  see `transport-equipment-repair.governor` ns docstring). The asset's
  own `:asset-verified?` / `:safety-concern-unresolved?` ground-truth
  fields are what the Repair Governor independently re-checks before
  ANY op may ever commit against it (see `transport-equipment-repair.
  governor`).

  The ledger stays append-only on every backend: 'which asset was
  logged, which repair operation was proposed, which safety concern was
  flagged and notified, which return-to-service coordination round was
  proposed, on what jurisdictional/asset-class basis, approved by whom'
  is always a query over an immutable log."
  (:require [transport-equipment-repair.registry :as registry]
            [langchain-store.core :as ls]
            [langchain.db :as d]))

(defprotocol Store
  (asset [s id])
  (all-assets [s])
  (ledger [s])
  (repair-record-log-history [s] "the append-only repair-record-log history (transport-equipment-repair.registry drafts)")
  (schedule-proposal-history [s] "the append-only repair-operation schedule-proposal history")
  (safety-concern-flag-history [s] "the append-only safety-concern-flag history")
  (return-to-service-coordination-history [s] "the append-only return-to-service coordination-proposal history")
  (next-repair-record-sequence [s jurisdiction])
  (next-schedule-sequence [s jurisdiction])
  (next-safety-concern-sequence [s jurisdiction])
  (next-return-to-service-sequence [s jurisdiction])
  (commit-record! [s record] "apply a committed op's PROPOSAL record to the SSoT -- see ns docstring, never a real-world actuation")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-assets [s asset-map] "replace/seed the asset directory (map id->asset)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained asset set spanning all three asset classes
  this ISIC class covers (marine-vessel / aircraft / railway-rolling-
  stock) across the happy path (asset-1 JPN marine vessel, asset-5 USA
  aircraft, asset-7 DEU/EU railway rolling stock), the uncovered-
  jurisdiction / not-verified / unresolved-safety-concern failure modes
  (asset-2..asset-4), and a further cross-asset-class / cross-
  jurisdiction happy path (asset-6 USA marine vessel) -- so the actor +
  tests run offline."
  []
  {:asset
   {"asset-1" {:id "asset-1" :name "Setouchi Marine Services -- Coastal Cargo Vessel Hull Structural Weld Repair (work order #7701)"
               :jurisdiction "JPN" :asset-class :marine-vessel :asset-verified? true
               :safety-concern-unresolved? false
               :safety-contacts [{:name "Hayato (repair technician)" :email "hayato@example.com" :phone "+819000000041"}
                                 {:name "Nozomi (shop safety officer)" :email "nozomi@example.com" :phone "+819000000042"}]
               :status :ready-to-schedule}
    "asset-2" {:id "asset-2" :name "Atlantis Shipyard -- Fishing Trawler Engine-Room Bulkhead Repair"
               :jurisdiction "ATL" :asset-class :marine-vessel :asset-verified? true
               :safety-concern-unresolved? false
               :safety-contacts []
               :status :intake}
    "asset-3" {:id "asset-3" :name "橋本航空整備工房 小型機主翼構造修理"
               :jurisdiction "JPN" :asset-class :aircraft :asset-verified? false
               :safety-concern-unresolved? false
               :safety-contacts [{:name "Hashimoto (repair technician)" :email "hashimoto@example.com" :phone "+819000000043"}]
               :status :unverified}
    "asset-4" {:id "asset-4" :name "ふくおか鉄道車両整備センター 台車枠亀裂修理"
               :jurisdiction "JPN" :asset-class :railway-rolling-stock :asset-verified? true
               :safety-concern-unresolved? true
               :safety-contacts [{:name "Fukuda (repair technician)" :email "fukuda@example.com" :phone "+819000000044"}
                                 {:name "Ogawa (shop safety officer)" :email "ogawa@example.com" :phone "+819000000045"}]
               :status :concern-open}
    "asset-5" {:id "asset-5" :name "Cascade Regional Air -- Turboprop Airframe Skin-Panel Repair"
               :jurisdiction "USA" :asset-class :aircraft :asset-verified? true
               :safety-concern-unresolved? false
               :safety-contacts [{:name "Morgan (repair technician)" :email "morgan@example.com" :phone "+15550000041"}]
               :status :ready-to-schedule}
    "asset-6" {:id "asset-6" :name "Bayfront Towing Co. -- Tugboat Rudder Assembly Overhaul"
               :jurisdiction "USA" :asset-class :marine-vessel :asset-verified? true
               :safety-concern-unresolved? false
               :safety-contacts [{:name "Dana (repair technician)" :email "dana@example.com" :phone "+15550000042"}]
               :status :ready-to-schedule}
    "asset-7" {:id "asset-7" :name "Rhein-Bahn Instandhaltungswerk -- Drehgestell-Radsatz-Reparatur"
               :jurisdiction "DEU" :asset-class :railway-rolling-stock :asset-verified? true
               :safety-concern-unresolved? false
               :safety-contacts [{:name "Weber (repair technician)" :email "weber@example.com" :phone "+4915000000041"}]
               :status :ready-to-schedule}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- log-repair-record!
  [s asset-id patch]
  (let [a (asset s asset-id)
        jurisdiction (or (:jurisdiction patch) (:jurisdiction a) "UNKNOWN")
        seq-n (next-repair-record-sequence s jurisdiction)
        result (registry/register-repair-record asset-id jurisdiction seq-n)]
    {:result result :jurisdiction jurisdiction :asset-patch patch}))

(defn- schedule-repair-operation!
  [s asset-id]
  (let [a (asset s asset-id)
        seq-n (next-schedule-sequence s (:jurisdiction a))
        result (registry/register-schedule-proposal asset-id (:jurisdiction a) seq-n)]
    {:result result}))

(defn- flag-safety-concern!
  [s asset-id concern-description]
  (let [a (asset s asset-id)
        seq-n (next-safety-concern-sequence s (:jurisdiction a))
        result (registry/register-safety-concern-flag asset-id (:jurisdiction a) seq-n)
        concern-number (get result "concern_number")
        doc (registry/render-safety-concern-notice a concern-number concern-description)]
    {:result (assoc-in result ["record" "document"] doc)
     :asset-patch {:safety-concern-unresolved? true}}))

(defn- coordinate-return-to-service!
  [s asset-id]
  (let [a (asset s asset-id)
        seq-n (next-return-to-service-sequence s (:jurisdiction a))
        result (registry/register-return-to-service-coordination asset-id (:jurisdiction a) seq-n)]
    {:result result}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (asset [_ id] (get-in @a [:asset id]))
  (all-assets [_] (sort-by :id (vals (:asset @a))))
  (ledger [_] (:ledger @a))
  (repair-record-log-history [_] (:repair-record-log @a))
  (schedule-proposal-history [_] (:schedule-proposals @a))
  (safety-concern-flag-history [_] (:safety-concern-flags @a))
  (return-to-service-coordination-history [_] (:return-to-service-coordinations @a))
  (next-repair-record-sequence [_ jurisdiction] (get-in @a [:repair-record-sequences jurisdiction] 0))
  (next-schedule-sequence [_ jurisdiction] (get-in @a [:schedule-sequences jurisdiction] 0))
  (next-safety-concern-sequence [_ jurisdiction] (get-in @a [:safety-concern-sequences jurisdiction] 0))
  (next-return-to-service-sequence [_ jurisdiction] (get-in @a [:return-to-service-sequences jurisdiction] 0))
  (commit-record! [s {:keys [op path value]}]
    (let [asset-id (first path)]
      (case op
        :log-repair-record
        (let [{:keys [result jurisdiction asset-patch]} (log-repair-record! s asset-id value)]
          (swap! a (fn [state]
                     (-> state
                         (update-in [:repair-record-sequences jurisdiction] (fnil inc 0))
                         (update-in [:asset asset-id] merge asset-patch)
                         (update :repair-record-log registry/append result))))
          result)

        :schedule-repair-operation
        (let [{:keys [result]} (schedule-repair-operation! s asset-id)
              jurisdiction (:jurisdiction (asset s asset-id))]
          (swap! a (fn [state]
                     (-> state
                         (update-in [:schedule-sequences jurisdiction] (fnil inc 0))
                         (update :schedule-proposals registry/append result))))
          result)

        :flag-safety-concern
        (let [{:keys [result asset-patch]} (flag-safety-concern! s asset-id (:concern-description value))
              jurisdiction (:jurisdiction (asset s asset-id))]
          (swap! a (fn [state]
                     (-> state
                         (update-in [:safety-concern-sequences jurisdiction] (fnil inc 0))
                         (update-in [:asset asset-id] merge asset-patch)
                         (update :safety-concern-flags registry/append result))))
          result)

        :coordinate-return-to-service
        (let [{:keys [result]} (coordinate-return-to-service! s asset-id)
              jurisdiction (:jurisdiction (asset s asset-id))]
          (swap! a (fn [state]
                     (-> state
                         (update-in [:return-to-service-sequences jurisdiction] (fnil inc 0))
                         (update :return-to-service-coordinations registry/append result))))
          result)

        nil))
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-assets [s asset-map] (when (seq asset-map) (swap! a assoc :asset asset-map)) s))

(defn seed-db
  "A MemStore seeded with the demo asset set. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :ledger []
                           :repair-record-sequences {} :repair-record-log []
                           :schedule-sequences {} :schedule-proposals []
                           :safety-concern-sequences {} :safety-concern-flags []
                           :return-to-service-sequences {} :return-to-service-coordinations []))))

;; ----------------------------- DatomicStore (langchain.db + langchain-store) -----------------------------

(def ^:private asset-spec
  "langchain-store.core field-spec for the `asset` entity -- drives
  `map->tx`/`pull->map`/`pull-pattern` from data instead of hand-written
  triples (ADR-2607141600)."
  {:id                          {:attr :asset/id}
   :name                        {:attr :asset/name}
   :jurisdiction                {:attr :asset/jurisdiction}
   :asset-class                 {:attr :asset/asset-class}
   :asset-verified?             {:attr :asset/asset-verified? :coerce boolean}
   :safety-concern-unresolved?  {:attr :asset/safety-concern-unresolved? :coerce boolean}
   :diagnostic-notes            {:attr :asset/diagnostic-notes}
   :safety-contacts             {:attr :asset/safety-contacts-edn :blob? true :default []}
   :status                      {:attr :asset/status}})

(def ^:private asset-pull (ls/pull-pattern asset-spec))

(defn- asset->tx [m] (ls/map->tx asset-spec m))
(defn- pull->asset [pulled] (ls/pull->map asset-spec :id pulled))

(def ^:private schema
  (ls/identity-schema [:asset/id
                       :ledger/seq
                       :repair-record-log/seq
                       :schedule-proposal/seq
                       :safety-concern-flag/seq
                       :return-to-service-coordination/seq
                       :repair-record-sequence/jurisdiction
                       :schedule-sequence/jurisdiction
                       :safety-concern-sequence/jurisdiction
                       :return-to-service-sequence/jurisdiction]))

(defrecord DatomicStore [conn]
  Store
  (asset [_ id]
    (pull->asset (d/pull (d/db conn) asset-pull [:asset/id id])))
  (all-assets [_]
    (->> (d/q '[:find [?id ...] :where [?e :asset/id ?id]] (d/db conn))
         (map #(pull->asset (d/pull (d/db conn) asset-pull [:asset/id %])))
         (sort-by :id)))
  (ledger [_] (ls/read-stream conn :ledger/seq :ledger/fact))
  (repair-record-log-history [_] (ls/read-stream conn :repair-record-log/seq :repair-record-log/record))
  (schedule-proposal-history [_] (ls/read-stream conn :schedule-proposal/seq :schedule-proposal/record))
  (safety-concern-flag-history [_] (ls/read-stream conn :safety-concern-flag/seq :safety-concern-flag/record))
  (return-to-service-coordination-history [_] (ls/read-stream conn :return-to-service-coordination/seq :return-to-service-coordination/record))
  (next-repair-record-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :repair-record-sequence/jurisdiction ?j] [?e :repair-record-sequence/next ?n]]
            (d/db conn) jurisdiction) 0))
  (next-schedule-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :schedule-sequence/jurisdiction ?j] [?e :schedule-sequence/next ?n]]
            (d/db conn) jurisdiction) 0))
  (next-safety-concern-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :safety-concern-sequence/jurisdiction ?j] [?e :safety-concern-sequence/next ?n]]
            (d/db conn) jurisdiction) 0))
  (next-return-to-service-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :return-to-service-sequence/jurisdiction ?j] [?e :return-to-service-sequence/next ?n]]
            (d/db conn) jurisdiction) 0))
  (commit-record! [s {:keys [op path value]}]
    (let [asset-id (first path)]
      (case op
        :log-repair-record
        (let [{:keys [result jurisdiction asset-patch]} (log-repair-record! s asset-id value)
              next-n (inc (next-repair-record-sequence s jurisdiction))]
          (d/transact! conn
                       [(asset->tx (assoc asset-patch :id asset-id))
                        {:repair-record-sequence/jurisdiction jurisdiction :repair-record-sequence/next next-n}])
          (ls/append-blob! conn :repair-record-log/seq :repair-record-log/record
                           (count (repair-record-log-history s)) (get result "record"))
          result)

        :schedule-repair-operation
        (let [{:keys [result]} (schedule-repair-operation! s asset-id)
              jurisdiction (:jurisdiction (asset s asset-id))
              next-n (inc (next-schedule-sequence s jurisdiction))]
          (d/transact! conn [{:schedule-sequence/jurisdiction jurisdiction :schedule-sequence/next next-n}])
          (ls/append-blob! conn :schedule-proposal/seq :schedule-proposal/record
                           (count (schedule-proposal-history s)) (get result "record"))
          result)

        :flag-safety-concern
        (let [{:keys [result asset-patch]} (flag-safety-concern! s asset-id (:concern-description value))
              jurisdiction (:jurisdiction (asset s asset-id))
              next-n (inc (next-safety-concern-sequence s jurisdiction))]
          (d/transact! conn
                       [(asset->tx (assoc asset-patch :id asset-id))
                        {:safety-concern-sequence/jurisdiction jurisdiction :safety-concern-sequence/next next-n}])
          (ls/append-blob! conn :safety-concern-flag/seq :safety-concern-flag/record
                           (count (safety-concern-flag-history s)) (get result "record"))
          result)

        :coordinate-return-to-service
        (let [{:keys [result]} (coordinate-return-to-service! s asset-id)
              jurisdiction (:jurisdiction (asset s asset-id))
              next-n (inc (next-return-to-service-sequence s jurisdiction))]
          (d/transact! conn [{:return-to-service-sequence/jurisdiction jurisdiction :return-to-service-sequence/next next-n}])
          (ls/append-blob! conn :return-to-service-coordination/seq :return-to-service-coordination/record
                           (count (return-to-service-coordination-history s)) (get result "record"))
          result)

        nil))
    s)
  (append-ledger! [s fact]
    (ls/append-blob! conn :ledger/seq :ledger/fact (count (ledger s)) fact)
    fact)
  (with-assets [s asset-map]
    (when (seq asset-map) (d/transact! conn (mapv asset->tx (vals asset-map)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data` ({:asset
  ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [asset]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-assets s asset))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo asset set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
