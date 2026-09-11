(ns transport-equipment-repair.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db + langchain-store) store satisfy the
  same contract is what makes 'swap the SSoT for Datomic / kotoba-server'
  a configuration change, not a rewrite -- see `fabricated-metal-repair.
  store-contract-test`/`installation.store-contract-test`/`electrical-
  equipment-repair.store-contract-test`/`other-equipment-repair.store-
  contract-test` for the same pattern on the sibling actors."
  (:require [clojure.test :refer [deftest is testing]]
            [transport-equipment-repair.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Setouchi Marine Services -- Coastal Cargo Vessel Hull Structural Weld Repair (work order #7701)" (:name (store/asset s "asset-1"))))
      (is (= "JPN" (:jurisdiction (store/asset s "asset-1"))))
      (is (= :marine-vessel (:asset-class (store/asset s "asset-1"))))
      (is (true? (:asset-verified? (store/asset s "asset-1"))))
      (is (false? (:safety-concern-unresolved? (store/asset s "asset-1"))))
      (is (false? (:asset-verified? (store/asset s "asset-3"))) "asset-3 seeded not-yet-verified")
      (is (= :aircraft (:asset-class (store/asset s "asset-3"))))
      (is (true? (:safety-concern-unresolved? (store/asset s "asset-4"))) "asset-4 seeded with an unresolved concern")
      (is (= :railway-rolling-stock (:asset-class (store/asset s "asset-4"))))
      (is (= "USA" (:jurisdiction (store/asset s "asset-5"))))
      (is (= "DEU" (:jurisdiction (store/asset s "asset-7"))))
      (is (= ["asset-1" "asset-2" "asset-3" "asset-4" "asset-5" "asset-6" "asset-7"]
             (mapv :id (store/all-assets s))))
      (is (= [] (store/ledger s)))
      (is (= [] (store/repair-record-log-history s)))
      (is (= [] (store/schedule-proposal-history s)))
      (is (= [] (store/safety-concern-flag-history s)))
      (is (= [] (store/return-to-service-coordination-history s)))
      (is (zero? (store/next-repair-record-sequence s "JPN")))
      (is (zero? (store/next-schedule-sequence s "JPN")))
      (is (zero? (store/next-safety-concern-sequence s "JPN")))
      (is (zero? (store/next-return-to-service-sequence s "JPN")))
      (is (= 2 (count (:safety-contacts (store/asset s "asset-1"))))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "log-repair-record commits a patch and appends a repair-record-log entry"
        (store/commit-record! s {:op :log-repair-record :path ["asset-1"]
                                 :value {:id "asset-1" :diagnostic-notes "loose hull-plate rivet"}})
        (is (= "loose hull-plate rivet" (:diagnostic-notes (store/asset s "asset-1"))))
        (is (= "Setouchi Marine Services -- Coastal Cargo Vessel Hull Structural Weld Repair (work order #7701)" (:name (store/asset s "asset-1"))) "unrelated field preserved")
        (is (= "JPN-RPR-000000" (get (first (store/repair-record-log-history s)) "record_id")))
        (is (= 1 (store/next-repair-record-sequence s "JPN"))))
      (testing "schedule-repair-operation drafts a record and advances the sequence"
        (store/commit-record! s {:op :schedule-repair-operation :path ["asset-1"]
                                 :value {:asset-id "asset-1" :window {}}})
        (is (= "JPN-SCH-000000" (get (first (store/schedule-proposal-history s)) "record_id")))
        (is (= "schedule-proposal-draft" (get (first (store/schedule-proposal-history s)) "kind")))
        (is (= 1 (store/next-schedule-sequence s "JPN"))))
      (testing "flag-safety-concern sets safety-concern-unresolved? true, drafts a record + notice document"
        (store/commit-record! s {:op :flag-safety-concern :path ["asset-1"]
                                 :value {:asset-id "asset-1" :concern-description "test structural crack"}})
        (is (true? (:safety-concern-unresolved? (store/asset s "asset-1"))))
        (is (= "JPN-SCF-000000" (get (first (store/safety-concern-flag-history s)) "record_id")))
        (is (some? (get (first (store/safety-concern-flag-history s)) "document")))
        (is (= 1 (store/next-safety-concern-sequence s "JPN"))))
      (testing "log-repair-record can resolve the concern"
        (store/commit-record! s {:op :log-repair-record :path ["asset-1"]
                                 :value {:id "asset-1" :safety-concern-unresolved? false}})
        (is (false? (:safety-concern-unresolved? (store/asset s "asset-1"))))
        (is (= 2 (store/next-repair-record-sequence s "JPN"))))
      (testing "coordinate-return-to-service drafts a record and advances the sequence"
        (store/commit-record! s {:op :coordinate-return-to-service :path ["asset-1"]
                                 :value {:asset-id "asset-1" :window {}}})
        (is (= "JPN-RTS-000000" (get (first (store/return-to-service-coordination-history s)) "record_id")))
        (is (= "return-to-service-coordination-draft" (get (first (store/return-to-service-coordination-history s)) "kind")))
        (is (= 1 (store/next-return-to-service-sequence s "JPN"))))
      (testing "sequences are jurisdiction-scoped, not global"
        (store/commit-record! s {:op :log-repair-record :path ["asset-5"]
                                 :value {:id "asset-5" :diagnostic-notes "corroded skin-panel fastener"}})
        (is (= "USA-RPR-000000" (get (last (store/repair-record-log-history s)) "record_id"))
            "USA gets its own independent sequence starting at 0, not continuing JPN's"))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/asset s "nope")))
    (is (= [] (store/all-assets s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/repair-record-log-history s)))
    (is (zero? (store/next-repair-record-sequence s "JPN")))
    (store/with-assets s {"x" {:id "x" :name "n" :jurisdiction "JPN" :asset-class :marine-vessel
                               :asset-verified? true
                               :safety-concern-unresolved? false
                               :status :intake}})
    (is (= "n" (:name (store/asset s "x"))))
    (is (true? (:asset-verified? (store/asset s "x"))))))
