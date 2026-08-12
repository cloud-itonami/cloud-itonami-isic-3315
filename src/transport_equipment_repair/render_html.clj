(ns transport-equipment-repair.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: it previously had NO
  demo page and no generator at all. This namespace drives the REAL
  actor stack -- `transport-equipment-repair.operation` (a compiled
  langgraph StateGraph) -> `transport-equipment-repair.governor` ->
  `transport-equipment-repair.store` -- through a scenario adapted from
  this repo's own `transport-equipment-repair.sim` demo driver
  (`clojure -M:dev:run`, run BEFORE this file was written to confirm it
  produces a sensible ledger against the real seeded asset ids
  `asset-1`..`asset-7`), then renders the resulting store, audit ledger
  and coordination-artifact registers.

  NOTHING on the page is hand-typed. Every asset id, name, jurisdiction,
  asset class, sequence number, disposition, hold rule and hold detail
  string is read back out of the real store/ledger the run produced. The
  action-gate table, the phase ladder and the regulatory spec-basis
  coverage table are derived from the live `transport-equipment-repair.
  governor` / `.phase` / `.facts` vars rather than described in prose, so
  they cannot drift away from the code. The scenario INPUTS (which op
  against which asset id) are of course authored -- that is what a
  scenario is -- but no OUTPUT is.

  ## Why this scenario

  It walks one asset through a full coordination episode (log a repair
  record -> propose a repair window -> flag a safety concern -> log its
  resolution -> re-propose the window -> coordinate the return to
  service), adds three cross-asset-class / cross-jurisdiction windows
  (USA aircraft, USA marine vessel, DEU/EU railway rolling stock -- all
  honestly qualitative; this actor's `facts` catalog has NO
  `:quantitative` (jurisdiction, asset-class) pair and never fabricates
  a numeric lead-time), and then exercises ALL SIX of the Repair
  Governor's HARD checks, each of which HOLDS without ever reaching a
  human:

    1. `:unknown-op`                -- an op outside the closed four-op allowlist
    2. `:effect-not-propose`        -- a deliberately ROGUE advisor injected over
                                       the SAME store, returning `:effect :actuate`.
                                       This check is unreachable from the shipped
                                       mock advisor (which always says `:propose`),
                                       so the only honest way to demonstrate the
                                       governor's defense-in-depth is to inject a
                                       malfunctioning advisor through the seam
                                       `operation/build` already exposes.
    3. `:forbidden-action-class`    -- a patch carrying this actor's transport-
                                       domain `:return-to-service-sign-off?`
                                       certification-authority marker
    4. `:asset-not-verified`        -- `asset-3`, `:asset-verified? false`
    5. `:no-legal-basis`            -- `asset-2`, jurisdiction ATL, no
                                       (jurisdiction, asset-class) pair in
                                       `transport-equipment-repair.facts`
    6. `:unresolved-safety-concern` -- `asset-4`, concern open on file

  ## Determinism

  Every collaborator in the path is pure or deterministic: the mock
  advisor is a `case` over the request, the registry's reference numbers
  are jurisdiction-scoped zero-padded sequences, the mock notifier writes
  to an atom, and no code in `src/` reads a clock or a RNG. Every set or
  map iterated for the page (`governor/closed-op-allowlist`,
  `phase/phases`, `facts/catalog`) is explicitly sorted here rather than
  iterated in hash order. The page therefore contains NO timestamp and
  NO generated id, and two consecutive runs are byte-identical (verified
  with `cmp`).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [jp-go-dds.skin]
            [langgraph.graph :as g]
            [transport-equipment-repair.advisor :as advisor]
            [transport-equipment-repair.facts :as facts]
            [transport-equipment-repair.governor :as governor]
            [transport-equipment-repair.notify :as notify]
            [transport-equipment-repair.operation :as op]
            [transport-equipment-repair.phase :as phase]
            [transport-equipment-repair.store :as store]))

(def ^:private operator
  "The same operator context this repo's own `sim` driver uses."
  {:actor-id "op-1" :actor-role :repair-shop-operator :phase 3})

;; ----------------------------- driving the REAL actor -----------------------------

(defn- record!
  "Append one finished graph run to the ordered run log. `result` is the
  raw `langgraph.graph/run*` return value -- everything rendered from it
  is real actor output."
  [runs tid request result]
  (swap! runs conj {:tid tid
                    :request request
                    :audit (vec (get-in result [:state :audit]))
                    :disposition (get-in result [:state :disposition])})
  result)

(defn- exec!
  "One operation, no human in the loop (auto-commit or HARD hold)."
  [runs actor tid request]
  (record! runs tid request
           (g/run* actor {:request request :context operator} {:thread-id tid})))

(defn- run-approve!
  "One operation that the phase gate / governor escalates, then resumed
  by a human approval. The resumed result carries the FULL accumulated
  audit (`:audit`'s reducer is `into`, restored from the checkpointer),
  so only the resumed result is recorded."
  [runs actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid})
  (record! runs tid request
           (g/run* actor {:approval {:status :approved :by "op-1"}}
                   {:thread-id tid :resume? true})))

(def ^:private rogue-advisor
  "A deliberately MALFUNCTIONING advisor -- it claims `:effect :actuate`,
  which the shipped mock advisor can never emit. Injected over the SAME
  store to prove HARD check 2 (`:effect-not-propose`) fires: a
  compromised or broken advisor gains nothing by trying. See ns
  docstring."
  (reify advisor/Advisor
    (-advise [_ _ request]
      {:summary    (str (:subject request) ": ROGUE advisor claiming a real-world actuation")
       :rationale  "この助言者は :effect :propose 以外を返す（本来あり得ない）"
       :cites      [:id]
       :effect     :actuate
       :value      {:id (:subject request)}
       :stake      nil
       :confidence 0.99})))

(defn run-demo!
  "Runs a fresh seeded store through the scenario described in the ns
  docstring. Returns `{:db :notifier :runs}` -- `:runs` is the ordered
  log of real graph results, `:db` the real store the actor wrote."
  []
  (let [db       (store/seed-db)
        notifier (notify/mock-notifier)
        actor    (op/build db {:notifier notifier})
        ;; same store, same notifier -- only the advisor is swapped
        broken   (op/build db {:notifier notifier :advisor rogue-advisor})
        runs     (atom [])]

    ;; --- clean coordination episode on asset-1 (JPN marine vessel) ---
    (exec! runs actor "t01"
           {:op :log-repair-record :subject "asset-1"
            :patch {:id "asset-1"
                    :diagnostic-notes "hairline crack found along the hull's longitudinal seam weld during ultrasonic scan"}})
    (exec! runs actor "t02"
           {:op :schedule-repair-operation :subject "asset-1"
            :window {:proposed-start-date "2026-08-01" :proposed-end-date "2026-08-03"}
            :notes "seam の再溶接、耐圧試験"})
    ;; ALWAYS escalates at every phase; the notice is really "sent" via the
    ;; mock notifier on commit, after the human approves.
    (run-approve! runs actor "t03"
                  {:op :flag-safety-concern :subject "asset-1"
                   :concern-type :structural-integrity-failure
                   :concern-description "船体外板の縦継手溶接部にヘアラインクラックを検出、再溶接範囲の再検討が必要。"})
    (exec! runs actor "t04"
           {:op :log-repair-record :subject "asset-1"
            :patch {:id "asset-1" :safety-concern-unresolved? false}})
    (exec! runs actor "t05"
           {:op :schedule-repair-operation :subject "asset-1"
            :window {:proposed-start-date "2026-08-05" :proposed-end-date "2026-08-06"}
            :notes "再溶接後の最終耐圧試験・外観検査"})
    (exec! runs actor "t06"
           {:op :coordinate-return-to-service :subject "asset-1"
            :window {:proposed-pickup-date "2026-08-08"}
            :notes "customerへの引き渡し物流調整のみ"})

    ;; --- cross-asset-class / cross-jurisdiction clean coordination ---
    (exec! runs actor "t07"
           {:op :schedule-repair-operation :subject "asset-5"
            :window {:proposed-start-date "2026-09-01" :proposed-end-date "2026-09-02"}})
    (exec! runs actor "t08"
           {:op :coordinate-return-to-service :subject "asset-6"
            :window {:proposed-pickup-date "2026-09-05"}})
    (exec! runs actor "t09"
           {:op :schedule-repair-operation :subject "asset-7"
            :window {:proposed-start-date "2026-09-10" :proposed-end-date "2026-09-11"}})

    ;; --- all six HARD checks, none of which ever reaches a human ---
    (exec! runs actor "t10" {:op :direct-equipment-command :subject "asset-1"})
    (exec! runs broken "t11" {:op :log-repair-record :subject "asset-1"
                              :patch {:id "asset-1"}})
    (exec! runs actor "t12" {:op :log-repair-record :subject "asset-1"
                             :patch {:id "asset-1" :return-to-service-sign-off? true}})
    (exec! runs actor "t13" {:op :log-repair-record :subject "asset-3"
                             :patch {:id "asset-3"}})
    (exec! runs actor "t14" {:op :schedule-repair-operation :subject "asset-2" :window {}})
    (exec! runs actor "t15" {:op :coordinate-return-to-service :subject "asset-4" :window {}})

    {:db db :notifier notifier :runs @runs}))

;; ----------------------------- rendering helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw-str [v] (if (keyword? v) (name v) (str v)))

(defn- code [v] (str "<code>" (esc v) "</code>"))

(defn- n-cell [v] (str "<span class=\"num\">" (esc v) "</span>"))

(defn- dash [] "<span class=\"muted\">&mdash;</span>")

(defn- bool-cell [v]
  (if (true? v)
    "<span class=\"ok\">yes</span>"
    "<span class=\"muted\">no</span>"))

(defn- fact-of [audit t] (first (filter #(= t (:t %)) audit)))

(defn- holds
  "The HARD `:governor-hold` facts the run actually wrote to the ledger."
  [db]
  (filterv #(= :governor-hold (:t %)) (store/ledger db)))

(defn- outcome
  "Classify one real run from its own audit trail. Never from a literal."
  [{:keys [audit disposition]}]
  (let [hold (fact-of audit :governor-hold)]
    (cond
      hold {:kind :hard-hold :violations (:violations hold)}

      (fact-of audit :approval-granted)
      {:kind :approved
       :reason (:reason (fact-of audit :approval-requested))
       :by (:by (fact-of audit :approval-granted))}

      (fact-of audit :approval-requested)
      {:kind :awaiting :reason (:reason (fact-of audit :approval-requested))}

      (= :commit disposition) {:kind :auto-commit}
      :else {:kind :other})))

(defn- outcome-cell [o]
  (case (:kind o)
    :hard-hold (str "<span class=\"critical\">HARD hold &middot; "
                    (esc (str/join ", " (map (comp kw-str :rule) (:violations o))))
                    "</span>")
    :approved (str "<span class=\"ok\">escalated (" (esc (kw-str (:reason o)))
                   ") &rarr; approved by " (esc (:by o)) "</span>")
    :awaiting (str "<span class=\"warn\">awaiting human approval &middot; "
                   (esc (kw-str (:reason o))) "</span>")
    :auto-commit "<span class=\"ok\">auto-commit (governor-clean)</span>"
    "<span class=\"muted\">in progress</span>"))

(defn- detail-cell [o]
  (case (:kind o)
    :hard-hold (esc (str/join " / " (map :detail (:violations o))))
    :approved "<span class=\"muted\">human in the loop before commit</span>"
    :awaiting "<span class=\"muted\">paused at :request-approval</span>"
    (dash)))

(defn- row [& cells]
  (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- rows [xs] (str/join "\n" xs))

(defn- op-codes
  "A deterministic, sorted `<code>` list of an op set."
  [ops]
  (if (seq ops)
    (str/join " " (map #(code (kw-str %)) (sort-by kw-str ops)))
    "<span class=\"muted\">none</span>"))

;; ----------------------------- sections (all derived) -----------------------------

(defn- asset-rows [db]
  (for [{:keys [id name jurisdiction asset-class asset-verified?
                safety-concern-unresolved? status diagnostic-notes safety-contacts]}
        (store/all-assets db)]
    (row (code id)
         (esc name)
         (esc jurisdiction)
         (code (kw-str asset-class))
         (bool-cell asset-verified?)
         (if (true? safety-concern-unresolved?)
           "<span class=\"critical\">open</span>"
           "<span class=\"ok\">none open</span>")
         (esc (kw-str status))
         (if (seq diagnostic-notes) (esc diagnostic-notes) (dash))
         (n-cell (count safety-contacts)))))

(defn- run-rows [db runs]
  (for [{:keys [tid request] :as r} runs
        :let [o (outcome r)
              a (store/asset db (:subject request))]]
    (row (code tid)
         (code (kw-str (:op request)))
         (code (:subject request))
         (esc (or (:jurisdiction a) "n/a"))
         (if (:asset-class a) (code (kw-str (:asset-class a))) (dash))
         (outcome-cell o)
         (detail-cell o))))

(defn- gate-rows
  "The action gate, DERIVED from the live governor/phase vars -- not a
  prose description that could drift away from the code."
  []
  (let [auto3 (get-in phase/phases [3 :auto])]
    (for [o (sort-by kw-str governor/closed-op-allowlist)
          :let [first-write-phase (first (for [p (sort (keys phase/phases))
                                               :when (contains? (:writes (get phase/phases p)) o)]
                                           p))]]
      (row (code (kw-str o))
           (if first-write-phase (n-cell first-write-phase) "<span class=\"muted\">never</span>")
           (if (contains? auto3 o)
             "<span class=\"ok\">may auto-commit when governor-clean</span>"
             "<span class=\"warn\">human approval, every phase</span>")
           (if (contains? governor/high-stakes o)
             "<span class=\"warn\">always high-stakes</span>"
             "<span class=\"muted\">no</span>")))))

(defn- phase-rows []
  (for [p (sort (keys phase/phases))
        :let [{:keys [label writes auto]} (get phase/phases p)]]
    (row (n-cell p) (esc label) (op-codes writes) (op-codes auto))))

(defn- spec-basis-rows
  "The regulatory catalog, read straight out of
  `transport-equipment-repair.facts/catalog`. ISIC 3315 spans THREE
  structurally disjoint regimes, so the catalog is keyed
  `iso3 -> asset-class`; both levels are sorted here for determinism."
  []
  (for [iso3 (sort (keys facts/catalog))
        asset-class (sort-by kw-str (keys (get facts/catalog iso3)))
        :let [{:keys [owner-authority repair-safety-provenance threshold-model
                      notification-lead-days]}
              (facts/spec-basis iso3 asset-class)]]
    (row (code iso3)
         (code (kw-str asset-class))
         (esc owner-authority)
         (str "<a href=\"" (esc repair-safety-provenance) "\">"
              (esc repair-safety-provenance) "</a>")
         (code (kw-str threshold-model))
         (if notification-lead-days (n-cell notification-lead-days) (dash)))))

(defn- ledger-rows [db]
  (for [{:keys [t op subject disposition basis violations summary]} (store/ledger db)]
    (row (case t
           :committed "<span class=\"ok\">committed</span>"
           :governor-hold "<span class=\"critical\">governor-hold</span>"
           :approval-rejected "<span class=\"critical\">approval-rejected</span>"
           (esc (kw-str t)))
         (code (kw-str op))
         (code subject)
         (esc (kw-str disposition))
         (if (seq violations)
           (esc (str/join ", " (map (comp kw-str :rule) violations)))
           (esc (str/join " ; " (map kw-str basis))))
         (if summary (esc summary) (dash)))))

(defn- artifact-rows [history]
  (for [r history]
    (row (code (get r "record_id"))
         (esc (get r "kind"))
         (code (get r "asset_id"))
         (esc (get r "jurisdiction"))
         (bool-cell (get r "immutable")))))

(defn- notifier-rows [notifier]
  (for [{:keys [status channel to subject message]} (notify/sent-log notifier)]
    (row (esc (kw-str channel))
         (esc to)
         (esc (or subject message))
         (if (= :sent status)
           "<span class=\"ok\">sent</span>"
           (str "<span class=\"critical\">" (esc (kw-str status)) "</span>")))))

(defn- section [title lead headers body-rows]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       "    <p class=\"muted\">" lead "</p>\n"
       "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" % "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n" (rows body-rows) "\n      </tbody>\n"
       "    </table>\n"
       "  </section>\n"))

;; ----------------------------- the document -----------------------------

(defn render
  "Renders the whole operator console from a `run-demo!` result. Takes no
  clock and no seed: identical input -> identical bytes."
  [{:keys [db notifier runs]}]
  (let [ledger    (vec (store/ledger db))
        outcomes  (mapv outcome runs)
        hs        (holds db)
        committed (filterv #(= :committed (:t %)) ledger)
        approved  (filterv #(= :approved (:kind %)) outcomes)
        auto      (filterv #(= :auto-commit (:kind %)) outcomes)
        cov       (facts/coverage)
        notices   (store/safety-concern-flag-history db)]
    (str
     "<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
     "<meta name=\"color-scheme\" content=\"light\">"
     "<title>cloud-itonami-isic-3315 &middot; repair of transport equipment, except motor vehicles "
     "&mdash; operator console</title>"
     "<style>" (jp-go-dds.skin/dds+skin) "</style></head><body>\n"

     "<header class=\"bar\">\n"
     "  <h1>Repair of transport equipment, except motor vehicles (ISIC 3315) &mdash; Operator Console</h1>\n"
     "</header>\n"
     "<p><span class=\"badge\">read-only sample</span> "
     "<span class=\"badge\">governor-gated</span> "
     "<span class=\"badge\">coordination-only &middot; every effect is :propose</span></p>\n"
     "<p class=\"subtitle\">Generated at build time by <code>transport-equipment-repair.render-html</code> "
     "(<code>clojure -M:dev:render-html</code>) by actually running the compiled "
     "<code>transport-equipment-repair.operation</code> StateGraph over a freshly seeded store. "
     "Every value below was read back out of that run &mdash; there is no mock markup on this page, "
     "and no timestamp, so successive regenerations are byte-identical.</p>\n"

     "<main>\n"

     (section "Run summary"
              "Counted from the real audit ledger and the real graph results, not asserted."
              ["Measure" "Count"]
              [(row "assets / work orders in the SSoT" (n-cell (count (store/all-assets db))))
               (row "graph runs in this scenario" (n-cell (count runs)))
               (row "<span class=\"ok\">auto-commits (governor-clean, phase 3)</span>" (n-cell (count auto)))
               (row "<span class=\"ok\">escalated &rarr; human-approved commits</span>" (n-cell (count approved)))
               (row "<span class=\"critical\">HARD governor holds (never reach a human)</span>" (n-cell (count hs)))
               (row "distinct HARD rules exercised"
                    (n-cell (count (into #{} (mapcat (fn [h] (map :rule (:violations h))) hs)))))
               (row "committed facts in the audit ledger" (n-cell (count committed)))
               (row "audit-ledger facts total" (n-cell (count ledger)))
               (row "confidence floor (<code>governor/confidence-floor</code>)" (n-cell governor/confidence-floor))
               (row "(jurisdiction, asset-class) pairs with an official spec-basis"
                    (n-cell (str (:covered cov) " / " (:requested cov))))])

     (section "Asset / work-order directory"
              "The SSoT after the run. <code>asset-verified?</code> and the open-concern flag are the
               ground truth the Repair Governor re-checks independently &mdash; never the advisor's own
               confidence. ISIC 3315 spans three asset classes; all three are seeded here."
              ["Id" "Asset / work order" "Jurisdiction" "Asset class" "Verified?" "Safety concern"
               "Status" "Diagnostic notes" "Safety contacts"]
              (asset-rows db))

     (section "Operation dispositions (this run)"
              "One row per graph run. The outcome and the hold reason are classified from each run's own
               audit trail; the detail text is the governor's own message, verbatim."
              ["Thread" "Op" "Subject" "Jurisdiction" "Asset class" "Outcome" "Governor detail"]
              (run-rows db runs))

     (section "Action gate (Repair Governor)"
              "Derived from <code>governor/closed-op-allowlist</code>, <code>governor/high-stakes</code>
               and <code>phase/phases</code> &mdash; if the code changes, this table changes.
               All six governor checks are HARD: a human approver cannot override them."
              ["Op" "Writable from phase" "At phase 3" "Permanent escalation"]
              (gate-rows))

     (section "Rollout phase ladder"
              "Read straight out of <code>transport-equipment-repair.phase/phases</code>."
              ["Phase" "Label" "Writes allowed" "May auto-commit"]
              (phase-rows))

     (section "Post-repair inspection / release-to-service basis"
              "Read straight out of <code>transport-equipment-repair.facts/catalog</code>. This catalog is
               keyed (jurisdiction, asset class) rather than by jurisdiction alone, because marine,
               aviation and rail sit under structurally disjoint regimes even within one jurisdiction.
               Every pair is honestly <code>:qualitative</code>: none of the researched sources sets a
               fixed numeric advance-notice lead time, and this actor does not invent one. A pair absent
               from this table has NO spec-basis, and the governor holds any schedule or return-to-service
               proposal against it."
              ["Jurisdiction" "Asset class" "Owner authority" "Official source" "Threshold model"
               "Lead days"]
              (spec-basis-rows))

     (section "Audit ledger"
              "Append-only decision facts the run actually wrote to the store."
              ["Fact" "Op" "Subject" "Disposition" "Basis / violated rule" "Summary"]
              (ledger-rows db))

     (section "Repair-record log"
              "Jurisdiction-scoped sequence numbers built by
               <code>transport-equipment-repair.registry</code>."
              ["Record id" "Kind" "Asset" "Jurisdiction" "Immutable"]
              (artifact-rows (store/repair-record-log-history db)))

     (section "Schedule proposals"
              "A proposed diagnostic / disassembly / repair / reassembly / test-line WINDOW &mdash;
               never a repair-equipment control command and never a certification-authority decision."
              ["Record id" "Kind" "Asset" "Jurisdiction" "Immutable"]
              (artifact-rows (store/schedule-proposal-history db)))

     (section "Safety-concern flags"
              "Always human-approved before commit, at every phase &mdash;
               <code>:flag-safety-concern</code> is absent from every phase's auto set, permanently."
              ["Record id" "Kind" "Asset" "Jurisdiction" "Immutable"]
              (artifact-rows (store/safety-concern-flag-history db)))

     (section "Return-to-service coordination proposals"
              "A completed-unit return-to-customer LOGISTICS coordination proposal (pickup / delivery
               scheduling, customer notification). Never an airworthiness / seaworthiness / rail-safety
               certification-authority sign-off."
              ["Record id" "Kind" "Asset" "Jurisdiction" "Immutable"]
              (artifact-rows (store/return-to-service-coordination-history db)))

     (section "Safety-concern notice dispatch"
              "The mock notifier's own send log &mdash; the notice really went out over both channels
               to every contact on the roster, after a human approved it."
              ["Channel" "To" "Subject / message" "Status"]
              (notifier-rows notifier))

     "  <section class=\"card\">\n"
     "    <h2>Safety-concern notice document</h2>\n"
     "    <p class=\"muted\">Rendered by "
     "<code>transport-equipment-repair.registry/render-safety-concern-notice</code> and stored verbatim "
     "on the flag record &mdash; it cites the (jurisdiction, asset-class) pair's own post-repair "
     "inspection-before-return-to-service legal basis inline, so the notice is self-evidencing.</p>\n"
     (str/join "\n" (for [n notices]
                      (str "    <pre><code>" (esc (get n "document")) "</code></pre>")))
     "\n  </section>\n"

     "</main>\n"
     "<footer>\n"
     "  <p>This actor NEVER operates repair equipment and NEVER signs off on an airworthiness,\n"
     "  seaworthiness or rail-safety certification-authority decision &mdash; that authority belongs\n"
     "  exclusively to the classification society, the airworthiness authority or the rail-safety\n"
     "  authority. Every proposal carries <code>:effect :propose</code>; committing one means a\n"
     "  coordination artifact was logged, never that a transport unit was certified fit to return\n"
     "  to service.</p>\n"
     "  <p>Regenerate: <code>clojure -M:dev:render-html</code></p>\n"
     "</footer>\n"
     "</body></html>\n")))

;; ----------------------------- entry point -----------------------------

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db runs] :as result} (run-demo!)
        hs (holds db)
        commits (filterv #(= :committed (:t %)) (store/ledger db))]
    ;; A console that shows no real HARD hold is not evidence of a governor.
    (when (empty? hs)
      (throw (ex-info "no :governor-hold fact on the ledger — refusing to write a console that shows no real hold"
                      {:ledger-facts (count (store/ledger db))})))
    ;; ...and one that shows no commit at all is not evidence of an actor.
    (when (empty? commits)
      (throw (ex-info "no :committed fact on the ledger — refusing to write a console that shows no clean path"
                      {:ledger-facts (count (store/ledger db))})))
    (let [f (java.io.File. ^String out)]
      (when-let [p (.getParentFile f)] (.mkdirs p))
      (spit f (render result)))
    (println "wrote" out
             (str "(" (count (store/ledger db)) " ledger facts, "
                  (count hs) " HARD holds, "
                  (count commits) " commits, "
                  (count runs) " requests, "
                  (count (store/repair-record-log-history db)) " repair records, "
                  (count (store/schedule-proposal-history db)) " schedule proposals, "
                  (count (store/safety-concern-flag-history db)) " safety-concern flags, "
                  (count (store/return-to-service-coordination-history db)) " return-to-service coordinations)"))))
