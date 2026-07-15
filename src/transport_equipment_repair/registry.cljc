(ns transport-equipment-repair.registry
  "Pure-function repair-record-log / schedule-proposal / safety-concern-
  flag / return-to-service-coordination-proposal record construction --
  an append-only repair-shop book-of-record draft, the transport-
  equipment-repair-domain analog of `fabricated-metal-repair.registry`/
  `installation.registry`/`demolition.registry`/`electrical-equipment-
  repair.registry`/`other-equipment-repair.registry`.

  Like every sibling actor's registry, there is no single international
  check-digit standard for any of these reference numbers -- every
  operator/jurisdiction assigns its own reference format. This namespace
  does NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `transport-equipment-repair.facts` uses.

  `render-safety-concern-notice` produces the actual human-readable
  document text sent to the asset's repair-technician/shop-safety-officer
  contact roster (`transport-equipment-repair.notify`) -- citing the
  jurisdiction and asset class's structural-integrity/airworthiness/
  seaworthiness/rail-safety-relevant post-repair inspection-before-
  return-to-service legal basis inline so the notice is self-evidencing
  about which law grounds the concern.

  This namespace is pure data + pure functions -- no I/O, no network call
  to any real regulatory filing system, no mail/phone send. It builds the
  RECORD/DOCUMENT this actor's `:effect :propose`-only proposals produce;
  it never builds, and this actor never proposes, a record that commands
  repair equipment or signs off on an airworthiness/seaworthiness/rail-
  safety-certification-authority decision (see `transport-equipment-
  repair.governor` ns docstring)."
  (:require [clojure.string :as str]
            [transport-equipment-repair.facts :as facts]))

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn- assert-record-fields! [op-label asset-id jurisdiction sequence]
  (when-not (and asset-id (not= asset-id ""))
    (throw (ex-info (str op-label ": asset_id required") {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info (str op-label ": jurisdiction required") {})))
  (when (< sequence 0)
    (throw (ex-info (str op-label ": sequence must be >= 0") {}))))

(defn register-repair-record
  "Validate + construct the REPAIR-RECORD-LOG registration DRAFT -- one
  entry in the append-only log of inspection/disassembly/repair/
  reassembly/test-line diagnostic-finding / repair-work-performed /
  parts-used data this actor's `:log-repair-record` op produces. Pure
  function -- does not verify or register anything itself; it builds the
  RECORD an operator would keep. `transport-equipment-repair.governor`
  independently re-verifies the asset's own recorded `:asset-verified?`
  ground-truth field before ANY op may commit against it."
  [asset-id jurisdiction sequence]
  (assert-record-fields! "repair-record" asset-id jurisdiction sequence)
  (let [record-id (str (str/upper-case jurisdiction) "-RPR-" (zero-pad sequence 6))]
    {"record" {"record_id" record-id "kind" "repair-record-log-entry"
               "asset_id" asset-id "jurisdiction" jurisdiction "immutable" true}
     "repair_record_number" record-id}))

(defn register-schedule-proposal
  "Validate + construct the REPAIR-OPERATION SCHEDULE-PROPOSAL DRAFT -- a
  proposed inspection/disassembly/repair/reassembly/test-line schedule
  window, NEVER a repair-equipment control command or an airworthiness/
  seaworthiness/rail-safety-certification-authority decision (that
  authority is the classification society's/airworthiness authority's/
  rail-safety authority's EXCLUSIVELY -- see README and `transport-
  equipment-repair.governor` ns docstring). Pure function -- `transport-
  equipment-repair.governor` independently re-verifies the asset is
  verified, its (jurisdiction, asset-class) pair's post-repair
  inspection-before-return-to-service legal basis is on file, and no
  safety concern is unresolved on file, before this is ever allowed to
  commit."
  [asset-id jurisdiction sequence]
  (assert-record-fields! "schedule-proposal" asset-id jurisdiction sequence)
  (let [record-id (str (str/upper-case jurisdiction) "-SCH-" (zero-pad sequence 6))]
    {"record" {"record_id" record-id "kind" "schedule-proposal-draft"
               "asset_id" asset-id "jurisdiction" jurisdiction "immutable" true}
     "schedule_number" record-id}))

(defn register-safety-concern-flag
  "Validate + construct the SAFETY-CONCERN-FLAG DRAFT -- surfacing a
  structural-integrity/airworthiness/seaworthiness/rail-safety concern
  for human review. Pure function -- `:flag-safety-concern` ALWAYS
  escalates to a human at every phase (see `transport-equipment-repair.
  phase`/`transport-equipment-repair.governor` ns docstrings), so this
  record is only ever committed after a human has reviewed it."
  [asset-id jurisdiction sequence]
  (assert-record-fields! "safety-concern-flag" asset-id jurisdiction sequence)
  (let [record-id (str (str/upper-case jurisdiction) "-SCF-" (zero-pad sequence 6))]
    {"record" {"record_id" record-id "kind" "safety-concern-flag-draft"
               "asset_id" asset-id "jurisdiction" jurisdiction "immutable" true}
     "concern_number" record-id}))

(defn register-return-to-service-coordination
  "Validate + construct the RETURN-TO-SERVICE COORDINATION-PROPOSAL DRAFT
  -- a completed-unit return-to-customer LOGISTICS coordination proposal
  (pickup/delivery scheduling, customer notification), NEVER an
  airworthiness/seaworthiness/rail-safety-certification-authority
  return-to-service sign-off (that authority is the classification
  society's/airworthiness authority's/rail-safety authority's
  EXCLUSIVELY -- see `transport-equipment-repair.governor` ns docstring
  forbidden-action-class check). Pure function -- does not itself
  release, authorize or certify anything; it builds the coordination
  RECORD an operator would keep."
  [asset-id jurisdiction sequence]
  (assert-record-fields! "return-to-service-coordination" asset-id jurisdiction sequence)
  (let [record-id (str (str/upper-case jurisdiction) "-RTS-" (zero-pad sequence 6))]
    {"record" {"record_id" record-id "kind" "return-to-service-coordination-draft"
               "asset_id" asset-id "jurisdiction" jurisdiction "immutable" true}
     "coordination_number" record-id}))

;; ----------------------------- notice document -----------------------------

(defn render-safety-concern-notice
  "Human-readable SAFETY-CONCERN NOTICE document text, citing the
  (jurisdiction, asset-class) pair's post-repair inspection-before-
  return-to-service legal basis inline -- the document sent (mail +
  phone, `transport-equipment-repair.notify`) to the asset's repair-
  technician/shop-safety-officer contact roster once a human has
  approved logging the concern. `asset` is the asset record at flag
  time; `concern-number` is from `register-safety-concern-flag`."
  [{asset-name :name :keys [id jurisdiction asset-class]} concern-number concern-description]
  (let [{:keys [repair-safety-basis repair-safety-provenance owner-authority]} (facts/spec-basis jurisdiction asset-class)]
    (str "# Transport-Equipment-Repair-Shop Safety-Concern Notice\n\n"
         "Concern number: " concern-number "\n"
         "Asset: " asset-name " (" id ")\n"
         "Jurisdiction: " jurisdiction "\n"
         "Asset class: " (some-> asset-class name) "\n"
         "Relevant authority: " (or owner-authority "n/a") "\n"
         "Related post-repair inspection-before-return-to-service basis: " (or repair-safety-basis "NOT COVERED -- no (jurisdiction, asset-class) spec-basis on file") "\n"
         "Source: " (or repair-safety-provenance "n/a") "\n\n"
         "## Concern description\n" (or concern-description "(not recorded)") "\n\n"
         "## Status\nThis is a COORDINATION NOTICE only -- it proposes nothing about "
         "repair-equipment control or an airworthiness/seaworthiness/rail-safety-"
         "certification-authority decision. Resolution and any actual return-to-"
         "service certification/sign-off remain the classification society's/"
         "airworthiness authority's/rail-safety authority's exclusive authority.\n")))

(defn append [history result]
  (conj (vec history) (get result "record")))
