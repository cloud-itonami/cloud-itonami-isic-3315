(ns transport-equipment-repair.registry-test
  (:require [clojure.test :refer [deftest is]]
            [transport-equipment-repair.registry :as r]))

;; ----------------------------- register-repair-record -----------------------------

(deftest repair-record-assigns-a-number
  (let [result (r/register-repair-record "asset-1" "JPN" 7)]
    (is (= (get result "repair_record_number") "JPN-RPR-000007"))
    (is (= (get-in result ["record" "asset_id"]) "asset-1"))
    (is (= (get-in result ["record" "kind"]) "repair-record-log-entry"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest repair-record-validation-rules
  (is (thrown? Exception (r/register-repair-record "" "JPN" 0)))
  (is (thrown? Exception (r/register-repair-record "asset-1" "" 0)))
  (is (thrown? Exception (r/register-repair-record "asset-1" "JPN" -1))))

;; ----------------------------- register-schedule-proposal -----------------------------

(deftest schedule-proposal-assigns-a-number
  (let [result (r/register-schedule-proposal "asset-1" "JPN" 3)]
    (is (= (get result "schedule_number") "JPN-SCH-000003"))
    (is (= (get-in result ["record" "kind"]) "schedule-proposal-draft"))))

(deftest schedule-proposal-validation-rules
  (is (thrown? Exception (r/register-schedule-proposal "" "JPN" 0)))
  (is (thrown? Exception (r/register-schedule-proposal "asset-1" "" 0)))
  (is (thrown? Exception (r/register-schedule-proposal "asset-1" "JPN" -1))))

;; ----------------------------- register-safety-concern-flag -----------------------------

(deftest safety-concern-flag-assigns-a-number
  (let [result (r/register-safety-concern-flag "asset-1" "JPN" 0)]
    (is (= (get result "concern_number") "JPN-SCF-000000"))
    (is (= (get-in result ["record" "kind"]) "safety-concern-flag-draft"))))

(deftest safety-concern-flag-validation-rules
  (is (thrown? Exception (r/register-safety-concern-flag "" "JPN" 0)))
  (is (thrown? Exception (r/register-safety-concern-flag "asset-1" "JPN" -1))))

;; ----------------------------- register-return-to-service-coordination -----------------------------

(deftest return-to-service-coordination-assigns-a-number
  (let [result (r/register-return-to-service-coordination "asset-1" "JPN" 0)]
    (is (= (get result "coordination_number") "JPN-RTS-000000"))
    (is (= (get-in result ["record" "kind"]) "return-to-service-coordination-draft"))))

(deftest return-to-service-coordination-validation-rules
  (is (thrown? Exception (r/register-return-to-service-coordination "" "JPN" 0)))
  (is (thrown? Exception (r/register-return-to-service-coordination "asset-1" "" 0)))
  (is (thrown? Exception (r/register-return-to-service-coordination "asset-1" "JPN" -1))))

;; ----------------------------- render-safety-concern-notice -----------------------------

(def sample-asset
  {:id "asset-1" :name "Setouchi Marine Services -- Coastal Cargo Vessel Hull Structural Weld Repair (work order #7701)"
   :jurisdiction "JPN" :asset-class :marine-vessel})

(deftest safety-concern-notice-cites-legal-basis-inline
  (let [doc (r/render-safety-concern-notice sample-asset "JPN-SCF-000000" "船体外板の縦継手溶接部にヘアラインクラックを検出")]
    (is (re-find #"JPN-SCF-000000" doc))
    (is (re-find #"船舶安全法" doc))
    (is (re-find #"laws\.e-gov\.go\.jp" doc))
    (is (re-find #"ヘアラインクラック" doc))
    (is (re-find #"marine-vessel" doc))
    (is (re-find #"classification society" doc) "honestly disclaims certification-authority sign-off authority")))

(deftest safety-concern-notice-varies-basis-by-asset-class-within-same-jurisdiction
  (let [aircraft-doc (r/render-safety-concern-notice (assoc sample-asset :asset-class :aircraft) "JPN-SCF-000001" "test")
        rail-doc (r/render-safety-concern-notice (assoc sample-asset :asset-class :railway-rolling-stock) "JPN-SCF-000002" "test")]
    (is (re-find #"航空法" aircraft-doc))
    (is (re-find #"鉄道に関する技術上の基準を定める省令" rail-doc))
    (is (not= aircraft-doc rail-doc) "same jurisdiction, different asset class -> different legal basis, never one combined law")))

(deftest safety-concern-notice-is-honest-about-uncovered-jurisdiction
  (let [doc (r/render-safety-concern-notice (assoc sample-asset :jurisdiction "ATL") "ATL-SCF-000000" "test")]
    (is (re-find #"NOT COVERED" doc))))

;; ----------------------------- append -----------------------------

(deftest history-is-append-only
  (let [c1 (r/register-repair-record "asset-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-repair-record "asset-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-RPR-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-RPR-000001" (get-in hist2 [1 "record_id"])))))
