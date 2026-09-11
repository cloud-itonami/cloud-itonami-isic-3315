(ns transport-equipment-repair.sim
  "Demo driver -- `clojure -M:dev:run`. Walks assets through a full
  coordination episode, exercising every governor check: log a repair
  record (auto-commits) -> propose a repair-operation schedule
  (governor-clean, asset-verified, AUTO-COMMITS at phase 3) -> flag a
  safety concern (ALWAYS escalates, human approves, safety-concern
  notice actually 'sent' via the mock notifier) -> log the concern's
  resolution (auto-commits) -> re-propose the schedule now that the
  concern is resolved (AUTO-COMMITS) -> coordinate return-to-service
  (AUTO-COMMITS at phase 3) -> then FOUR HARD holds that never reach a
  human at all: an uncovered jurisdiction, a not-independently-verified
  asset record, an unresolved safety concern, and an op outside the
  closed four-op allowlist -- then a cross-asset-class (USA aircraft,
  honestly qualitative) and a cross-asset-class/cross-jurisdiction (DEU
  railway rolling stock, honestly qualitative, never fabricates a
  numeric lead-time) schedule walkthrough. Finally prints the audit
  ledger + every coordination-artifact history."
  (:require [langgraph.graph :as g]
            [transport-equipment-repair.store :as store]
            [transport-equipment-repair.notify :as notify]
            [transport-equipment-repair.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :repair-shop-operator :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        notifier (notify/mock-notifier)
        actor (op/build db {:notifier notifier})]
    (println "== log-repair-record asset-1 (JPN marine vessel, diagnostic finding, no ground-truth booleans touched) (AUTO-COMMITS) ==")
    (println (exec! actor "t1" {:op :log-repair-record :subject "asset-1"
                                :patch {:id "asset-1" :diagnostic-notes "hairline crack found along the hull's longitudinal seam weld during ultrasonic scan"}} operator))

    (println "== schedule-repair-operation asset-1 (clean, asset-verified -- AUTO-COMMITS at phase 3) ==")
    (println (exec! actor "t2" {:op :schedule-repair-operation :subject "asset-1"
                                :window {:proposed-start-date "2026-08-01" :proposed-end-date "2026-08-03"}
                                :notes "seam の再溶接、耐圧試験"} operator))

    (println "== flag-safety-concern asset-1 (ALWAYS escalates -- human approves; safety-concern notice actually sent via mock notifier) ==")
    (let [r (exec! actor "t3" {:op :flag-safety-concern :subject "asset-1"
                               :concern-type :structural-integrity-failure
                               :concern-description "船体外板の縦継手溶接部にヘアラインクラックを検出、再溶接範囲の再検討が必要。"} operator)]
      (println r)
      (println (approve! actor "t3")))
    (println "-- sent log --")
    (println (notify/sent-log notifier))

    (println "== log-repair-record asset-1: concern resolved after inspection (AUTO-COMMITS) ==")
    (println (exec! actor "t4" {:op :log-repair-record :subject "asset-1"
                                :patch {:id "asset-1" :safety-concern-unresolved? false}} operator))

    (println "== schedule-repair-operation asset-1 AGAIN, now that the concern is resolved (AUTO-COMMITS) ==")
    (println (exec! actor "t5" {:op :schedule-repair-operation :subject "asset-1"
                                :window {:proposed-start-date "2026-08-05" :proposed-end-date "2026-08-06"}
                                :notes "再溶接後の最終耐圧試験・外観検査"} operator))

    (println "== coordinate-return-to-service asset-1 (clean, no unresolved concern -- AUTO-COMMITS at phase 3; NEVER a certification-authority sign-off) ==")
    (println (exec! actor "t6" {:op :coordinate-return-to-service :subject "asset-1"
                                :window {:proposed-pickup-date "2026-08-08"}
                                :notes "customerへの引き渡し物流調整のみ"} operator))

    (println "== schedule-repair-operation asset-2 (ATL, no spec-basis -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t7" {:op :schedule-repair-operation :subject "asset-2" :window {}} operator))

    (println "== log-repair-record asset-3 (asset-verified? false -> HARD hold even for logging, never reaches a human; broader-than-3311 domain invariant) ==")
    (println (exec! actor "t8" {:op :log-repair-record :subject "asset-3" :patch {:id "asset-3"}} operator))

    (println "== schedule-repair-operation asset-4 (safety-concern-unresolved? true on file -> HARD hold) ==")
    (println (exec! actor "t9" {:op :schedule-repair-operation :subject "asset-4" :window {}} operator))

    (println "== :direct-equipment-command asset-1 (outside the closed 4-op allowlist -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t10" {:op :direct-equipment-command :subject "asset-1"} operator))

    (println "== USA (honestly qualitative -- no fixed federal numeric lead-time) -- schedule-repair-operation asset-5 (aircraft, AUTO-COMMITS, governor-clean) ==")
    (println (exec! actor "t11" {:op :schedule-repair-operation :subject "asset-5"
                                 :window {:proposed-start-date "2026-09-01" :proposed-end-date "2026-09-02"}} operator))

    (println "== DEU/EU (qualitative -- no fixed numeric lead-time, never fabricated) -- schedule-repair-operation asset-7 (railway rolling stock, AUTO-COMMITS, governor-clean) ==")
    (println (exec! actor "t12" {:op :schedule-repair-operation :subject "asset-7"
                                 :window {:proposed-start-date "2026-09-10" :proposed-end-date "2026-09-11"}} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== repair-record-log ==")
    (doseq [r (store/repair-record-log-history db)] (println r))

    (println "== schedule-proposal history ==")
    (doseq [r (store/schedule-proposal-history db)] (println r))

    (println "== safety-concern-flag history ==")
    (doseq [r (store/safety-concern-flag-history db)] (println (get r "document")))

    (println "== return-to-service-coordination history ==")
    (doseq [r (store/return-to-service-coordination-history db)] (println r))))
