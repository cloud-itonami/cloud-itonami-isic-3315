(ns transport-equipment-repair.facts-test
  (:require [clojure.test :refer [deftest is]]
            [transport-equipment-repair.facts :as facts]))

;; ----------------------------- JPN -----------------------------

(deftest jpn-marine-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN" :marine-vessel)))
  (is (string? (:repair-safety-provenance (facts/spec-basis "JPN" :marine-vessel))))
  (is (= :qualitative (:threshold-model (facts/spec-basis "JPN" :marine-vessel))))
  (is (nil? (:notification-lead-days (facts/spec-basis "JPN" :marine-vessel)))))

(deftest jpn-aircraft-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN" :aircraft)))
  (is (= :qualitative (:threshold-model (facts/spec-basis "JPN" :aircraft)))))

(deftest jpn-rail-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN" :railway-rolling-stock)))
  (is (= :qualitative (:threshold-model (facts/spec-basis "JPN" :railway-rolling-stock)))))

;; ----------------------------- USA -----------------------------

(deftest usa-marine-is-honestly-qualitative-not-fabricated
  (is (= :qualitative (:threshold-model (facts/spec-basis "USA" :marine-vessel))))
  (is (nil? (:notification-lead-days (facts/spec-basis "USA" :marine-vessel)))))

(deftest usa-aircraft-is-honestly-qualitative-not-fabricated
  (is (= :qualitative (:threshold-model (facts/spec-basis "USA" :aircraft))))
  (is (nil? (:notification-lead-days (facts/spec-basis "USA" :aircraft)))))

(deftest usa-rail-is-honestly-qualitative-not-fabricated
  (is (= :qualitative (:threshold-model (facts/spec-basis "USA" :railway-rolling-stock))))
  (is (nil? (:notification-lead-days (facts/spec-basis "USA" :railway-rolling-stock)))))

;; ----------------------------- DEU -----------------------------

(deftest deu-marine-is-honestly-qualitative-not-fabricated
  (is (= :qualitative (:threshold-model (facts/spec-basis "DEU" :marine-vessel)))))

(deftest deu-aircraft-is-honestly-qualitative-not-fabricated
  (is (= :qualitative (:threshold-model (facts/spec-basis "DEU" :aircraft)))))

(deftest deu-rail-is-honestly-qualitative-not-fabricated
  (is (= :qualitative (:threshold-model (facts/spec-basis "DEU" :railway-rolling-stock)))))

;; ----------------------------- uncovered pairs -----------------------------

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL" :marine-vessel))))

(deftest known-jurisdiction-unknown-asset-class-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "JPN" :motor-vehicle))
      "3315 explicitly excludes motor vehicles -- no spec-basis entry for that asset class, ever"))

(deftest coverage-never-reports-a-missing-pair-as-covered
  (let [report (facts/coverage [["JPN" :marine-vessel] ["ATL" :marine-vessel] ["USA" :aircraft]])]
    (is (= 2 (:covered report)))
    (is (= [["ATL" :marine-vessel]] (:missing-jurisdictions report)))
    (is (= [["JPN" :marine-vessel] ["USA" :aircraft]] (:covered-jurisdictions report)))))

(deftest full-coverage-report-finds-all-nine-seeded-pairs
  (let [report (facts/coverage)]
    (is (= 9 (:requested report)))
    (is (= 9 (:covered report)))
    (is (empty? (:missing-jurisdictions report)))))

;; ----------------------------- notification-lead-insufficient? -----------------------------
;; UNLIKE installation.facts (JPN quantitative), this catalog found NO
;; quantitative (jurisdiction, asset-class) pair among the nine seeded --
;; every covered pair always returns :qualitative, never a fabricated
;; true/false.

(deftest jpn-marine-never-gets-a-fabricated-true-false
  (is (= :qualitative (facts/notification-lead-insufficient? "JPN" :marine-vessel {})))
  (is (= :qualitative (facts/notification-lead-insufficient? "JPN" :marine-vessel {:anything 100}))))

(deftest usa-aircraft-never-gets-a-fabricated-true-false
  (is (= :qualitative (facts/notification-lead-insufficient? "USA" :aircraft {})))
  (is (= :qualitative (facts/notification-lead-insufficient? "USA" :aircraft {:anything 0}))))

(deftest deu-rail-never-gets-a-fabricated-true-false
  (is (= :qualitative (facts/notification-lead-insufficient? "DEU" :railway-rolling-stock {})))
  (is (= :qualitative (facts/notification-lead-insufficient? "DEU" :railway-rolling-stock {:anything 0}))))

(deftest unknown-pair-returns-nil-not-a-guess
  (is (nil? (facts/notification-lead-insufficient? "ATL" :marine-vessel {:anything 100}))))

(deftest no-seeded-pair-in-this-catalog-is-ever-quantitative
  (is (every? #(= :qualitative (:threshold-model %))
              (for [iso3 (keys facts/catalog) asset-class (keys (get facts/catalog iso3))]
                (facts/spec-basis iso3 asset-class)))
      "cloud-itonami-isic-3315's honest research found zero quantitative (jurisdiction, asset-class) pairs for the post-repair inspection-before-return-to-service duty -- see ns docstring"))

;; ----------------------------- catalog citation honesty -----------------------------

(deftest jpn-marine-cites-real-ship-safety-act-temporary-survey-provision
  (let [sb (facts/spec-basis "JPN" :marine-vessel)]
    (is (re-find #"船舶安全法" (:repair-safety-basis sb)))
    (is (re-find #"第5条" (:repair-safety-basis sb)))
    (is (re-find #"laws\.e-gov\.go\.jp" (:repair-safety-provenance sb)))))

(deftest jpn-aircraft-cites-real-civil-aeronautics-act-provision
  (let [sb (facts/spec-basis "JPN" :aircraft)]
    (is (re-find #"航空法" (:repair-safety-basis sb)))
    (is (re-find #"第19条" (:repair-safety-basis sb)))
    (is (re-find #"laws\.e-gov\.go\.jp" (:repair-safety-provenance sb)))))

(deftest jpn-rail-cites-real-technical-standards-ordinance-provision
  (let [sb (facts/spec-basis "JPN" :railway-rolling-stock)]
    (is (re-find #"鉄道に関する技術上の基準を定める省令" (:repair-safety-basis sb)))
    (is (re-find #"第89条" (:repair-safety-basis sb)))
    (is (re-find #"laws\.e-gov\.go\.jp" (:repair-safety-provenance sb)))))

(deftest usa-marine-cites-real-coast-guard-vessel-repair-regulation
  (let [sb (facts/spec-basis "USA" :marine-vessel)]
    (is (re-find #"46 CFR" (:repair-safety-basis sb)))
    (is (re-find #"2\.01-15" (:repair-safety-basis sb)))
    (is (re-find #"law\.cornell\.edu" (:repair-safety-provenance sb)))))

(deftest usa-aircraft-cites-real-faa-return-to-service-provision
  (let [sb (facts/spec-basis "USA" :aircraft)]
    (is (re-find #"14 CFR 43\.5" (:repair-safety-basis sb)))
    (is (re-find #"return to service" (:repair-safety-basis sb)))
    (is (re-find #"ecfr\.gov" (:repair-safety-provenance sb)))))

(deftest usa-rail-cites-real-fra-freight-car-safety-standards-provision
  (let [sb (facts/spec-basis "USA" :railway-rolling-stock)]
    (is (re-find #"49 CFR Part 215" (:repair-safety-basis sb)))
    (is (re-find #"215\.9" (:repair-safety-basis sb)))
    (is (re-find #"ecfr\.gov" (:repair-safety-provenance sb)))))

(deftest deu-marine-cites-real-eu-ship-inspection-organisations-regulation
  (let [sb (facts/spec-basis "DEU" :marine-vessel)]
    (is (re-find #"391/2009" (:repair-safety-basis sb)))
    (is (re-find #"eur-lex\.europa\.eu" (:repair-safety-provenance sb)))))

(deftest deu-aircraft-cites-real-easa-part-145-provision
  (let [sb (facts/spec-basis "DEU" :aircraft)]
    (is (re-find #"145\.A\.50" (:repair-safety-basis sb)))
    (is (re-find #"easa\.europa\.eu" (:repair-safety-provenance sb)))))

(deftest deu-rail-cites-real-ecm-certification-regulation
  (let [sb (facts/spec-basis "DEU" :railway-rolling-stock)]
    (is (re-find #"2019/779" (:repair-safety-basis sb)))
    (is (re-find #"eur-lex\.europa\.eu" (:repair-safety-provenance sb)))))

(deftest uncovered-pair-has-no-fabricated-catalog-entry
  (is (nil? (facts/spec-basis "ATL" :marine-vessel))))
