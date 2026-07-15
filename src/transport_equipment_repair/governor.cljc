(ns transport-equipment-repair.governor
  "Repair Governor -- the independent compliance layer named in
  `blueprint.edn` (`:itonami.blueprint/governor :transport-equipment-
  repair-governor`) that earns the Repair Advisor the right to commit.
  The LLM has no notion of transport-equipment-repair-shop safety law,
  whether an asset's own recorded verification actually reflects
  reality, or when a proposal has quietly drifted outside this actor's
  charter into repair-equipment control or an airworthiness/
  seaworthiness/rail-safety-certification-authority decision, so this
  MUST be a separate system able to *reject* a proposal and fall back to
  HOLD -- the transport-equipment-repair analog of `cloud-itonami-isic-
  3311`'s Repair Governor, `cloud-itonami-isic-3314`'s Repair Governor
  and `cloud-itonami-isic-3319`'s Repair Governor.

  ## Scope -- this is a COORDINATION-ONLY actor

  This actor NEVER controls repair equipment and NEVER signs off on an
  airworthiness/seaworthiness/rail-safety-certification-authority
  decision (e.g. a return-to-service certificate) -- that authority is
  the classification society's/airworthiness authority's/rail-safety
  authority's EXCLUSIVELY. Every proposal this actor's Repair Advisor can
  produce carries `:effect :propose` and NOTHING else -- committing a
  proposal here means 'this coordination artifact is now logged/
  scheduled/flagged/coordinated', never 'a repair tool was operated' or
  'a transport unit is certified fit to return to service'. Checks 1-4
  below encode this scope as STRUCTURAL, permanent HARD holds -- not
  policy that could be relaxed by a future phase, unlike checks 5-6,
  which are ordinary per-(jurisdiction, asset-class)/ground-truth safety
  gates.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them.

    1. Unknown op                -- the proposal's `:op` is outside the
                                     CLOSED four-op allowlist
                                     (`:log-repair-record` / `:schedule-
                                     repair-operation` / `:flag-safety-
                                     concern` / `:coordinate-return-to-
                                     service`). Permanent, structural.
    2. Effect is not :propose    -- ANY proposal whose `:effect` is not
                                     literally `:propose` is rejected,
                                     unconditionally. Defense-in-depth: a
                                     compromised/malfunctioning advisor
                                     can never slip a real-world actuation
                                     effect past this governor. Permanent,
                                     structural.
    3. Forbidden action class    -- a proposal whose `:value` carries a
                                     `:repair-equipment-control?` /
                                     `:direct-actuation?` /
                                     `:airworthiness-certification-
                                     decision?` / `:seaworthiness-
                                     certification-decision?` / `:rail-
                                     safety-certification-decision?` /
                                     `:return-to-service-sign-off?`
                                     marker true is rejected,
                                     unconditionally -- even though this
                                     actor's own mock advisor never sets
                                     these, the governor checks
                                     independently so a compromised
                                     advisor gains nothing by trying.
                                     Permanent, structural, un-overridable
                                     by ANY human approval -- see README
                                     `Actuation`.
    4. Asset not independently
       verified/registered        -- for ALL FOUR ops (a genuine domain
                                     difference from `fabricated-metal-
                                     repair.governor`'s equivalent check,
                                     which exempts `:log-repair-record`
                                     -- see below), the asset's own
                                     recorded `:asset-verified?` ground-
                                     truth field must be true. This
                                     actor's explicit domain-design
                                     requirement is that a repair-order/
                                     asset record must be independently
                                     verified/registered BEFORE ANY
                                     action, not merely before the
                                     downstream schedule/flag/coordinate
                                     ops -- the same broader-than-3311
                                     posture `cloud-itonami-isic-0130`'s
                                     batch-registration-before-any-action
                                     invariant established for its own
                                     domain. `:asset-verified?` is set
                                     ONLY by an independent process
                                     outside this actor (e.g. the vessel's
                                     flag-administration registration, the
                                     aircraft's airworthiness
                                     registration, the rolling stock's
                                     fleet registration) -- this actor's
                                     own `:log-repair-record` op can PATCH
                                     other ground-truth fields (diagnostic
                                     notes, concern resolution) on an
                                     already-verified asset, but never
                                     originates the `:asset-verified?`
                                     flag itself, so this check never
                                     creates a bootstrapping deadlock.
    5. Legal-basis missing       -- for `:schedule-repair-operation` AND
                                     `:coordinate-return-to-service`, did
                                     the proposal cite an OFFICIAL source
                                     for the asset's (jurisdiction,
                                     asset-class) pair (`transport-
                                     equipment-repair.facts`), or invent
                                     one for an uncovered pair? Applied to
                                     BOTH ops (broader than `fabricated-
                                     metal-repair.governor`'s schedule-
                                     only check) because coordinating the
                                     actual return-to-customer of a ship/
                                     aircraft/railway-rolling-stock unit
                                     is just as safety-relevant as
                                     scheduling the repair work itself.
    6. Unresolved safety concern -- for `:schedule-repair-operation` AND
                                     `:coordinate-return-to-service`, the
                                     asset's own recorded `:safety-
                                     concern-unresolved?` ground-truth
                                     field must be false. Evaluated off
                                     the STORE's ground truth, never the
                                     current proposal's own confidence --
                                     the same 'ground truth, not
                                     self-report' discipline
                                     `installation.governor/unresolved-
                                     safety-concern-violations` /
                                     `fabricated-metal-repair.governor`
                                     established. Applied to BOTH ops for
                                     the same reason as check 5. Does NOT
                                     fire for `:flag-safety-concern`
                                     itself (that op ALWAYS escalates to
                                     a human regardless of its own
                                     content -- see check on `high-
                                     stakes` below and `transport-
                                     equipment-repair.phase` ns
                                     docstring).

  The confidence/high-stakes gate is SOFT: it asks a human to look, and
  the human may approve.

    - `:flag-safety-concern` is UNCONDITIONALLY a member of `high-stakes`
      -- it ALWAYS escalates to a human, at every phase, regardless of
      confidence or governor cleanliness. Surfacing a structural-
      integrity/airworthiness/seaworthiness/rail-safety concern is
      exactly the kind of judgment this actor must never let auto-
      commit.
    - `:schedule-repair-operation` and `:coordinate-return-to-service`
      are DELIBERATELY NOT permanent members of `high-stakes` -- the
      SAME escalation shape `cloud-itonami-isic-3311`/`cloud-itonami-
      isic-3314`/`cloud-itonami-isic-3319` use for their own schedule
      ops: MAY auto-commit at phase 3 when the governor is completely
      clean (see `transport-equipment-repair.phase` ns docstring
      'Actuation' section) -- checks 3-6 above (forbidden-action-class
      including every certification-authority-decision marker, asset-
      verification, legal-basis, unresolved-concern) already gate the
      real hazard surface independently of phase; `:coordinate-return-
      to-service` itself is still only ever a proposed LOGISTICS
      coordination (pickup/delivery scheduling, customer notification),
      never an airworthiness/seaworthiness/rail-safety-certification-
      authority sign-off."
  (:require [transport-equipment-repair.store :as store]))

(def confidence-floor 0.6)

(def closed-op-allowlist
  #{:log-repair-record :schedule-repair-operation :flag-safety-concern :coordinate-return-to-service})

(def high-stakes
  "Ops that ALWAYS escalate to a human when the governor is otherwise
  clean, at every phase, unconditionally. `:log-repair-record` is
  deliberately NOT a member -- low-risk data logging/normalization, the
  same posture `fabricated-metal-repair.governor/high-stakes` gives
  `:log-repair-record`. `:schedule-repair-operation` and `:coordinate-
  return-to-service` are ALSO deliberately NOT members -- see ns
  docstring for why this actor's escalation surface stays aligned with
  `cloud-itonami-isic-3311`/`cloud-itonami-isic-3314`/`cloud-itonami-
  isic-3319`."
  #{:flag-safety-concern})

;; ----------------------------- checks -----------------------------

(defn- unknown-op-violations
  "The proposal's `:op` must be a member of the CLOSED four-op allowlist.
  Permanent, structural -- see ns docstring check 1."
  [{:keys [op]}]
  (when-not (contains? closed-op-allowlist op)
    [{:rule :unknown-op
      :detail (str op " はこのアクターの許可された4オペレーション（:log-repair-record/"
                  ":schedule-repair-operation/:flag-safety-concern/:coordinate-return-to-service）"
                  "のいずれにも該当しない")}]))

(defn- effect-not-propose-violations
  "Every proposal from this actor's advisor must carry `:effect
  :propose` -- and nothing else. Permanent, structural -- see ns
  docstring check 2."
  [proposal]
  (when-not (= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str "提案の:effectが:propose以外（" (pr-str (:effect proposal))
                  "）-- このアクターは提案のみで、実際の作動/確定を一切行わない")}]))

(defn- forbidden-action-class-violations
  "A proposal's `:value` must never carry a repair-equipment-control /
  direct-actuation / airworthiness-certification-decision / seaworthiness-
  certification-decision / rail-safety-certification-decision /
  return-to-service-sign-off marker. Permanent, structural, un-
  overridable by any human approval -- see ns docstring check 3."
  [proposal]
  (let [v (:value proposal)]
    (when (and (map? v)
               (or (true? (:repair-equipment-control? v))
                   (true? (:direct-actuation? v))
                   (true? (:airworthiness-certification-decision? v))
                   (true? (:seaworthiness-certification-decision? v))
                   (true? (:rail-safety-certification-decision? v))
                   (true? (:return-to-service-sign-off? v))))
      [{:rule :forbidden-action-class
        :detail "修理設備の直接操作コマンド、または耐空性/堪航性/鉄道安全certification authorityの決定（返品・稼働再開return-to-serviceの確定を含む）を伴う提案は恒久的に禁止（classification society/airworthiness authority/rail-safety authorityの専権事項）"}])))

(defn- asset-not-verified-violations
  "For ALL FOUR ops, the asset's own recorded `:asset-verified?`
  ground-truth field must be true. See ns docstring check 4 -- broader
  than `fabricated-metal-repair.governor`'s equivalent check, a genuine,
  documented domain difference."
  [{:keys [subject]} st]
  (let [a (store/asset st subject)]
    (when-not (true? (:asset-verified? a))
      [{:rule :asset-not-verified
        :detail (str subject " は現有記録が独立して検証・登録済み（:asset-verified?）でない状態での提案")}])))

(defn- legal-basis-missing-violations
  "For `:schedule-repair-operation` and `:coordinate-return-to-service`,
  the proposal must cite an OFFICIAL source for the asset's
  (jurisdiction, asset-class) pair -- never invent one for an uncovered
  pair. See ns docstring check 5."
  [{:keys [op]} proposal]
  (when (contains? #{:schedule-repair-operation :coordinate-return-to-service} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-legal-basis
          :detail "公式legal-basisの引用が無い提案は輸送用機器修理作業スケジュール/稼働再開調整提案として扱えない"}]))))

(defn- unresolved-safety-concern-violations
  "For `:schedule-repair-operation` and `:coordinate-return-to-service`,
  the asset's own recorded `:safety-concern-unresolved?` ground-truth
  field must be false. See ns docstring check 6."
  [{:keys [op subject]} st]
  (when (contains? #{:schedule-repair-operation :coordinate-return-to-service} op)
    (let [a (store/asset st subject)]
      (when (true? (:safety-concern-unresolved? a))
        [{:rule :unresolved-safety-concern
          :detail (str subject " は未解決の安全性懸念（構造健全性/耐空性/堪航性/鉄道安全）がある状態でのスケジュール/稼働再開調整提案")}]))))

(defn check
  "Censors a Repair Advisor proposal against the governor rules. Returns
  {:ok? bool :violations [..] :confidence c :escalate? bool :high-stakes?
  bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (unknown-op-violations request)
                           (effect-not-propose-violations proposal)
                           (forbidden-action-class-violations proposal)
                           (asset-not-verified-violations request st)
                           (legal-basis-missing-violations request proposal)
                           (unresolved-safety-concern-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
