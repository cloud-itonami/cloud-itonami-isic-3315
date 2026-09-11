(ns transport-equipment-repair.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:flag-safety-concern` must NEVER be a member of any
  phase's `:auto` set. `:log-repair-record`, `:schedule-repair-operation`
  and `:coordinate-return-to-service` ARE auto-eligible at phase 3 -- see
  `transport-equipment-repair.phase` ns docstring 'Actuation' section,
  matching the shape `cloud-itonami-isic-3311`/`cloud-itonami-isic-3314`/
  `cloud-itonami-isic-3319` use."
  (:require [clojure.test :refer [deftest is testing]]
            [transport-equipment-repair.phase :as phase]))

(deftest flag-safety-concern-never-auto-at-any-phase
  (doseq [[n {:keys [auto]}] phase/phases]
    (is (not (contains? auto :flag-safety-concern))
        (str "phase " n " must not auto-commit :flag-safety-concern"))))

(deftest log-repair-record-schedule-repair-operation-and-coordinate-return-to-service-are-auto-eligible-at-phase-3
  (is (contains? (:auto (get phase/phases 3)) :log-repair-record))
  (is (contains? (:auto (get phase/phases 3)) :schedule-repair-operation))
  (is (contains? (:auto (get phase/phases 3)) :coordinate-return-to-service)))

(deftest schedule-repair-operation-is-not-auto-eligible-before-phase-3
  (doseq [n [0 1 2]]
    (is (not (contains? (:auto (get phase/phases n)) :schedule-repair-operation))
        (str "phase " n " must not auto-commit :schedule-repair-operation"))))

(deftest coordinate-return-to-service-is-not-auto-eligible-before-phase-3
  (doseq [n [0 1 2]]
    (is (not (contains? (:auto (get phase/phases n)) :coordinate-return-to-service))
        (str "phase " n " must not auto-commit :coordinate-return-to-service"))))

(deftest write-ops-is-exactly-the-closed-four-op-allowlist
  (is (= #{:log-repair-record :schedule-repair-operation :flag-safety-concern :coordinate-return-to-service}
         phase/write-ops)))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-1-only-allows-log-repair-record
  (is (= #{:log-repair-record} (:writes (get phase/phases 1)))))

(deftest phase-2-allows-log-flag-and-coordinate-but-not-schedule
  (is (= #{:log-repair-record :flag-safety-concern :coordinate-return-to-service}
         (:writes (get phase/phases 2)))))

(deftest phase-3-auto-set-is-exactly-log-schedule-and-coordinate
  (is (= #{:log-repair-record :schedule-repair-operation :coordinate-return-to-service} (:auto (get phase/phases 3)))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :log-repair-record} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :flag-safety-concern} :commit)))))

(deftest gate-auto-commits-log-repair-record-when-clean-at-phase-3
  (is (= :commit (:disposition (phase/gate 3 {:op :log-repair-record} :commit)))))

(deftest gate-auto-commits-schedule-repair-operation-when-clean-at-phase-3
  (is (= :commit (:disposition (phase/gate 3 {:op :schedule-repair-operation} :commit)))))

(deftest gate-auto-commits-coordinate-return-to-service-when-clean-at-phase-3
  (is (= :commit (:disposition (phase/gate 3 {:op :coordinate-return-to-service} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 1 {:op :flag-safety-concern} :commit))))
  (is (= :hold (:disposition (phase/gate 0 {:op :log-repair-record} :commit)))))

(deftest gate-holds-schedule-repair-operation-at-phase-2-even-when-clean
  (testing "phase 2 doesn't enable :schedule-repair-operation as a write at all -> HOLD, not escalate"
    (is (= :hold (:disposition (phase/gate 2 {:op :schedule-repair-operation} :commit))))))

(deftest gate-escalates-coordinate-return-to-service-at-phase-2-even-when-clean
  (testing "phase 2 enables :coordinate-return-to-service as a write but not as auto -> ESCALATE"
    (is (= :escalate (:disposition (phase/gate 2 {:op :coordinate-return-to-service} :commit))))))
