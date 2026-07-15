(ns transport-equipment-repair.governor-contract-test
  "The governor contract as executable tests -- the transport-equipment-
  repair-coordination analog of `fabricated-metal-repair.governor-
  contract-test`/`installation.governor-contract-test`/`electrical-
  equipment-repair.governor-contract-test`/`other-equipment-repair.
  governor-contract-test`. The single invariant under test:

    Repair Advisor never schedules a repair operation, files a safety-
    concern flag or coordinates a return-to-service the Repair Governor
    would reject; `:flag-safety-concern` NEVER auto-commits at any
    phase; `:log-repair-record` (governor-clean, no direct capital/
    safety risk), `:schedule-repair-operation` (governor-clean) and
    `:coordinate-return-to-service` (governor-clean) MAY auto-commit
    when clean; and every decision (commit OR hold) leaves exactly one
    ledger fact. Every committed record's `:effect` is `:propose` --
    this actor never performs a real-world actuation."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [transport-equipment-repair.store :as store]
            [transport-equipment-repair.operation :as op]
            [transport-equipment-repair.governor :as governor]
            [transport-equipment-repair.phase :as phase]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :repair-shop-operator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

;; ----------------------------- :log-repair-record -----------------------------

(deftest clean-log-repair-record-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :log-repair-record :subject "asset-1" :patch {:id "asset-1" :diagnostic-notes "loose hull-plate rivet"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "loose hull-plate rivet" (:diagnostic-notes (store/asset db "asset-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))
    (is (= "JPN-RPR-000000" (get (first (store/repair-record-log-history db)) "record_id")))))

(deftest log-repair-record-can-resolve-a-safety-concern
  (let [[db actor] (fresh)]
    (exec-op actor "t1b" {:op :log-repair-record :subject "asset-4" :patch {:id "asset-4" :safety-concern-unresolved? false}} operator)
    (is (false? (:safety-concern-unresolved? (store/asset db "asset-4"))))))

(deftest log-repair-record-on-an-unverified-asset-is-held
  (testing "asset-3 has asset-verified? false -> HARD hold even for LOGGING, never reaches a human -- broader-than-3311 domain invariant (see governor ns docstring check 4)"
    (let [[db actor] (fresh)
          res (exec-op actor "t1c" {:op :log-repair-record :subject "asset-3" :patch {:id "asset-3" :diagnostic-notes "x"}} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:asset-not-verified} (-> (store/ledger db) first :basis)))
      (is (empty? (store/repair-record-log-history db))))))

;; ----------------------------- :schedule-repair-operation -----------------------------

(deftest clean-schedule-repair-operation-auto-commits
  (testing "asset-1 is fully clean (verified, no unresolved concern) -- auto-commits at phase 3, same shape cloud-itonami-isic-3311/3314/3319 use"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :schedule-repair-operation :subject "asset-1" :window {}} operator)]
      (is (= :commit (get-in res [:state :disposition])))
      (is (= "JPN-SCH-000000" (get (first (store/schedule-proposal-history db)) "record_id")))
      (is (= 1 (count (store/schedule-proposal-history db)))))))

(deftest fabricated-jurisdiction-is-held
  (testing "asset-2 (ATL, no spec-basis in transport-equipment-repair.facts) -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3" {:op :schedule-repair-operation :subject "asset-2" :window {}} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-legal-basis} (-> (store/ledger db) first :basis)))
      (is (empty? (store/schedule-proposal-history db)) "no schedule proposal recorded"))))

(deftest not-independently-verified-asset-is-held
  (testing "asset-3 has asset-verified? false -> HARD hold, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :schedule-repair-operation :subject "asset-3" :window {}} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:asset-not-verified} (-> (store/ledger db) first :basis)))
      (is (empty? (store/schedule-proposal-history db))))))

(deftest unresolved-safety-concern-is-held
  (testing "asset-4 has safety-concern-unresolved? true on file -> HARD hold, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t5" {:op :schedule-repair-operation :subject "asset-4" :window {}} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:unresolved-safety-concern} (-> (store/ledger db) first :basis)))
      (is (empty? (store/schedule-proposal-history db))))))

(deftest qualitative-pair-never-fabricates-a-numeric-hold-and-still-auto-commits-when-clean
  (testing "asset-7 (DEU/EU railway rolling stock, qualitative) -- no numeric bright line, but governor-clean so it still auto-commits"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :schedule-repair-operation :subject "asset-7" :window {}} operator)]
      (is (= :commit (get-in res [:state :disposition])))
      (is (= "DEU-SCH-000000" (get (first (store/schedule-proposal-history db)) "record_id"))))))

(deftest usa-cross-asset-class-happy-path-also-auto-commits
  (testing "asset-5 (USA aircraft, honestly qualitative) -- governor-clean, auto-commits"
    (let [[db actor] (fresh)
          res (exec-op actor "t7" {:op :schedule-repair-operation :subject "asset-5" :window {}} operator)]
      (is (= :commit (get-in res [:state :disposition])))
      (is (= "USA-SCH-000000" (get (first (store/schedule-proposal-history db)) "record_id"))))))

;; ----------------------------- :flag-safety-concern -----------------------------

(deftest flag-safety-concern-always-escalates-even-when-clean
  (testing "asset-1 is fully clean -- :flag-safety-concern STILL always interrupts, unconditionally"
    (let [[db actor] (fresh)
          r1 (exec-op actor "t10" {:op :flag-safety-concern :subject "asset-1"
                                   :concern-type :structural-integrity-failure
                                   :concern-description "hairline crack found along the hull's longitudinal seam weld"} operator)]
      (is (= :interrupted (:status r1)))
      (let [r2 (approve! actor "t10")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:safety-concern-unresolved? (store/asset db "asset-1"))))
        (is (some? (get (first (store/safety-concern-flag-history db)) "document")) "rendered notice document present")))))

(deftest flag-safety-concern-triggers-notification-only-after-approval
  (let [[_db actor] (fresh)
        r1 (exec-op actor "t11" {:op :flag-safety-concern :subject "asset-1"
                                 :concern-type :airworthiness-concern :concern-description "minor skin-panel disbond detected during post-repair inspection"} operator)]
    (is (nil? (:notify-result (:state r1))) "no notify before human approval")
    (let [r2 (approve! actor "t11")
          notify-result (:notify-result (:state r2))]
      (is (= 2 (count notify-result)) "one result entry per asset-1 safety-contact")
      (is (every? #(= :sent (get-in % [:mail :status])) notify-result))
      (is (every? #(= :sent (get-in % [:phone :status])) notify-result)))))

(deftest flag-safety-concern-on-an-unverified-asset-is-hard-held-not-merely-escalated
  (testing "asset-3 (asset-verified? false) -- the asset-verification HARD check gates this before the high-stakes gate even matters; confirms hold wins over escalate"
    (let [[db actor] (fresh)
          r1 (exec-op actor "t11b" {:op :flag-safety-concern :subject "asset-3"
                                    :concern-type :airworthiness-concern :concern-description "x"} operator)]
      (is (= :done (:status r1)) "a HARD hold completes the run outright -- it never reaches :interrupted")
      (is (= :hold (get-in r1 [:state :disposition])))
      (is (some #{:asset-not-verified} (-> (store/ledger db) first :basis))))))

;; ----------------------------- :coordinate-return-to-service -----------------------------

(deftest coordinate-return-to-service-below-clean-auto-commits
  (testing "asset-1, governor-clean (verified, no unresolved concern, legal-basis on file) -- AUTO-COMMITS, never a certification-authority sign-off"
    (let [[db actor] (fresh)
          res (exec-op actor "t12" {:op :coordinate-return-to-service :subject "asset-1" :window {}} operator)]
      (is (= :commit (get-in res [:state :disposition])))
      (is (= 1 (count (store/return-to-service-coordination-history db))))
      (is (= "JPN-RTS-000000" (get (first (store/return-to-service-coordination-history db)) "record_id"))))))

(deftest coordinate-return-to-service-with-unresolved-concern-is-held
  (testing "asset-4 has safety-concern-unresolved? true on file -> HARD hold, never reaches a human -- same gate as schedule-repair-operation"
    (let [[db actor] (fresh)
          res (exec-op actor "t13" {:op :coordinate-return-to-service :subject "asset-4" :window {}} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:unresolved-safety-concern} (-> (store/ledger db) first :basis)))
      (is (empty? (store/return-to-service-coordination-history db))))))

(deftest coordinate-return-to-service-in-an-uncovered-jurisdiction-is-held
  (testing "asset-2 (ATL, no spec-basis) -> HARD hold"
    (let [[db actor] (fresh)
          res (exec-op actor "t14" {:op :coordinate-return-to-service :subject "asset-2" :window {}} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-legal-basis} (-> (store/ledger db) first :basis))))))

;; ----------------------------- closed op-allowlist -----------------------------

(deftest op-outside-the-closed-allowlist-is-held
  (testing "an op outside {:log-repair-record :schedule-repair-operation :flag-safety-concern :coordinate-return-to-service} -> HARD hold, never reaches a human, regardless of what the advisor's default branch returns"
    (let [[db actor] (fresh)
          res (exec-op actor "t15" {:op :direct-equipment-command :subject "asset-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:unknown-op} (-> (store/ledger db) first :basis))))))

;; ----------------------------- effect / forbidden-action-class (direct governor/check) -----------------------------
;; The mock advisor never produces these -- they exercise the governor's
;; defense-in-depth against a hypothetically compromised/malfunctioning
;; advisor, so they are tested directly against `governor/check` rather
;; than through the full actor (which can only ever see what the mock
;; advisor actually proposes).

(deftest effect-not-propose-is-a-permanent-hard-violation
  (let [db (store/seed-db)
        request {:op :log-repair-record :subject "asset-1"}
        proposal {:summary "s" :rationale "r" :cites ["x"] :effect :direct-write
                  :value {:id "asset-1"} :stake nil :confidence 0.99}
        verdict (governor/check request {} proposal db)]
    (is (:hard? verdict))
    (is (some #{:effect-not-propose} (map :rule (:violations verdict))))
    (is (not (:ok? verdict)))))

(deftest forbidden-action-class-markers-are-permanent-hard-violations
  (doseq [marker [:repair-equipment-control? :direct-actuation?
                  :airworthiness-certification-decision? :seaworthiness-certification-decision?
                  :rail-safety-certification-decision? :return-to-service-sign-off?]]
    (testing marker
      (let [db (store/seed-db)
            request {:op :schedule-repair-operation :subject "asset-1"}
            proposal {:summary "s" :rationale "r" :cites ["x"] :effect :propose
                      :value {marker true} :stake :schedule-repair-operation :confidence 0.99}
            verdict (governor/check request {} proposal db)]
        (is (:hard? verdict))
        (is (some #{:forbidden-action-class} (map :rule (:violations verdict))))))))

;; ----------------------------- ledger discipline -----------------------------

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :log-repair-record :subject "asset-1" :patch {:id "asset-1" :diagnostic-notes "x"}} operator)
      (exec-op actor "b" {:op :schedule-repair-operation :subject "asset-2" :window {}} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))

(deftest approver-rejection-is-held-not-committed
  (let [[db actor] (fresh)
        r1 (exec-op actor "t16" {:op :flag-safety-concern :subject "asset-1"
                                 :concern-type :structural-integrity-failure :concern-description "test"} operator)]
    (is (= :interrupted (:status r1)))
    (let [r2 (g/run* actor {:approval {:status :rejected :by "op-1"}} {:thread-id "t16" :resume? true})]
      (is (= :hold (get-in r2 [:state :disposition])))
      (is (empty? (store/safety-concern-flag-history db))))))

;; ----------------------------- phase structural invariants (belt-and-suspenders) -----------------------------

(deftest flag-never-auto-at-any-phase
  (testing "structural invariant: :flag-safety-concern is never auto-eligible, even when clean, at any phase"
    (is (= :escalate (:disposition (phase/gate 3 {:op :flag-safety-concern} :commit)))
        ":flag-safety-concern must escalate to a human even when the governor is clean at phase 3")))

(deftest schedule-and-coordinate-return-are-auto-eligible-at-phase-3-same-as-3311-3314-3319
  (testing "structural invariant: this actor's escalation surface auto-commits governor-clean schedule/coordinate-return-to-service ops at phase 3"
    (is (= :commit (:disposition (phase/gate 3 {:op :schedule-repair-operation} :commit))))
    (is (= :commit (:disposition (phase/gate 3 {:op :coordinate-return-to-service} :commit))))))
