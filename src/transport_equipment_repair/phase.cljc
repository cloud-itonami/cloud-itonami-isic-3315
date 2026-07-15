(ns transport-equipment-repair.phase
  "Phase 0->3 staged rollout -- the transport-equipment-repair-
  coordination analog of `fabricated-metal-repair.phase`/`installation.
  phase`/`demolition.phase`/`electrical-equipment-repair.phase`/`other-
  equipment-repair.phase`.

    Phase 0  read-only              -- no writes, still governor-gated.
    Phase 1  assisted-logging       -- `:log-repair-record` allowed,
                                       every write needs human approval.
    Phase 2  assisted-coordination  -- adds `:flag-safety-concern` and
                                       `:coordinate-return-to-service`
                                       writes, still approval.
    Phase 3  supervised-coordination -- adds `:schedule-repair-
                                       operation`; governor-clean,
                                       high-confidence `:log-repair-
                                       record` (pure data logging, no
                                       capital/safety risk), `:schedule-
                                       repair-operation` (governor-clean,
                                       asset-verified, legal-basis on
                                       file, no unresolved concern) AND
                                       `:coordinate-return-to-service`
                                       (governor-clean, same gates) may
                                       auto-commit.

  ## Actuation (there is none -- read this before changing this file)

  This actor performs NO real-world actuation. Every proposal it can ever
  produce carries `:effect :propose` (see `transport-equipment-repair.
  governor` ns docstring checks 1-4) -- 'committing' a proposal here
  means only that a coordination artifact (a repair-record-log entry, a
  schedule PROPOSAL, a safety-concern flag, a return-to-service
  coordination PROPOSAL) is now logged in the SSoT + audit ledger. It
  never operates repair equipment, never signs off on an airworthiness/
  seaworthiness/rail-safety-certification-authority decision -- that
  authority is the classification society's/airworthiness authority's/
  rail-safety authority's exclusively.

  `:flag-safety-concern` is DELIBERATELY ABSENT from every phase's
  `:auto` set, including phase 3 -- a permanent structural fact, not a
  rollout milestone still to come. Surfacing a structural-integrity/
  airworthiness/seaworthiness/rail-safety concern is exactly the
  judgment this actor must never let auto-commit; it is always a human's
  call. `transport-equipment-repair.governor`'s `high-stakes` set
  enforces the same invariant independently -- two layers, not one,
  agree on this (see `transport-equipment-repair.governor` ns
  docstring).

  `:schedule-repair-operation` and `:coordinate-return-to-service` ARE
  members of phase 3's `:auto` set here -- the SAME shape `cloud-
  itonami-isic-3311`/`cloud-itonami-isic-3314`/`cloud-itonami-isic-3319`
  use for their own schedule ops. `transport-equipment-repair.governor`'s
  own asset-verification / legal-basis / unresolved-concern HARD checks
  (checks 4-6) PLUS its forbidden-action-class block on every
  certification-authority-decision marker (check 3) already gate the
  actual structural/airworthiness/seaworthiness/rail-safety hazard
  surface independently of phase -- both ops are still only ever a
  proposed diagnostic/repair/testing WINDOW or a proposed LOGISTICS
  coordination, never a live-work authorization or a certification-
  authority sign-off. This is a deliberate design choice matching this
  fleet's established shape, not an oversight -- see `transport-
  equipment-repair.governor` ns docstring `high-stakes`.

  `:log-repair-record` (inspection/disassembly/repair/reassembly/test-
  line DATA LOGGING, no direct capital or safety risk on an already-
  verified asset) is ALSO a member of phase 3's `:auto` set -- the same
  'phase says maybe, governor decides' layering `installation.phase`/
  `construction.phase`/`fabricated-metal-repair.phase`/`electrical-
  equipment-repair.phase`/`other-equipment-repair.phase` established.")

(def read-ops  #{})
(def write-ops #{:log-repair-record :schedule-repair-operation
                 :flag-safety-concern :coordinate-return-to-service})

;; NOTE the invariant: `:flag-safety-concern` is a member of `write-ops`
;; (governor-gated like any write) but is NEVER a member of any phase's
;; `:auto` set below. Do not add it there -- see ns docstring 'Actuation'
;; section above before changing this.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"                 :writes #{}                                            :auto #{}}
   1 {:label "assisted-logging"          :writes #{:log-repair-record}                           :auto #{}}
   2 {:label "assisted-coordination"     :writes #{:log-repair-record :flag-safety-concern
                                                    :coordinate-return-to-service}                :auto #{}}
   3 {:label "supervised-coordination"   :writes write-ops
      :auto #{:log-repair-record :schedule-repair-operation :coordinate-return-to-service}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:flag-safety-concern` is never auto-eligible at any phase, so it
    always escalates once the governor clears it (or holds if the
    governor doesn't). `:log-repair-record`, `:schedule-repair-operation`
    and `:coordinate-return-to-service` MAY auto-commit at phase 3 -- see
    ns docstring."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Repair Governor verdict to a base disposition before the phase
  gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
