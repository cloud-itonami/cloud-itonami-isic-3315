# cloud-itonami-isic-3315

Open Business Blueprint for **ISIC Rev.5 3315**: repair of transport
equipment, except motor vehicles.

This repository designs a forkable OSS business for transport-equipment-
repair-shop operations coordination: run by a qualified operator so a
community keeps its own operating records instead of renting a closed
SaaS.

ISIC 3315 covers repair of **transport equipment, except motor
vehicles** -- ships and boats, aircraft and spacecraft, and railway
locomotives and rolling stock -- distinct from sibling repair classes
3311 (fabricated metal products), 3312 (machinery and equipment), 3313
(electronic and optical equipment), 3314 (electrical equipment) and the
residual class 3319 (other equipment), and distinct from motor-vehicle
repair (a different ISIC section entirely).

## Scope -- this is a COORDINATION-ONLY actor, not equipment control or a certification authority

This is a safety-relevant domain: transport-equipment repair can involve
structural-integrity, airworthiness, seaworthiness and rail-safety risk.
**This actor does NOT hold repair-equipment control authority, and it
does NOT hold airworthiness/seaworthiness/rail-safety-certification-
authority.** Both are the classification society's/airworthiness
authority's/rail-safety authority's exclusive authority, always. The
Repair Advisor (LLM) never issues an equipment-control command and never
signs off on returning a repaired ship, aircraft or rail vehicle to
service; the independent **Repair Governor** HARD-blocks any proposal
that even tries (un-overridable by any human approval -- see
`transport-equipment-repair.governor` ns docstring). This actor
coordinates *potential* diagnostic/repair/testing dispatch and completed-
unit return-to-customer logistics (a proposed schedule window, a flagged
concern, a return-to-service coordination proposal) -- it never directly
actuates and never certifies fitness for service.

Structurally, EVERY proposal this actor's advisor can produce carries
`:effect :propose`, and the Repair Governor HARD-holds any proposal that
doesn't -- this is a permanent invariant distinguishing this actor from
actors whose sibling ops DO commit real-world effects.

## Core Contract

```text
asset (ship/aircraft/rail vehicle) + repair-order record
independently verified/registered BEFORE ANY action
        |
        v
Advisor -> Repair Governor -> proceed (log/schedule/flag/coordinate proposal), hold, or human approval
        |
        v
coordination artifacts (schedule proposal, safety-concern flag,
return-to-service coordination proposal) + audit ledger -- NEVER
repair-equipment dispatch, NEVER an airworthiness/seaworthiness/
rail-safety-certification-authority sign-off
```

No automated advice can propose a schedule the governor refuses,
suppress a safety-concern flag, or slip an equipment-control/
certification-authority-decision marker past the governor -- and
`:flag-safety-concern` always needs a human sign-off regardless of how
clean the governor's check comes back (see `Actuation` below).

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `3315`). Required capabilities:

- `:identity`
- `:forms`
- `:audit-ledger`

## Implemented slice (`src/transport_equipment_repair`)

`blueprint.edn` names the governor `:transport-equipment-repair-governor`
and is now `:implemented`. This repo implements it end-to-end --
**Repair Advisor ⊣ Repair Governor** -- following the SAME `.cljc` actor
pattern (langgraph-clj StateGraph, mock-by-default advisor, dual
MemStore/Datomic backend, 0→3 phase rollout) every prior
`cloud-itonami-isic-*` actor in this fleet uses, structured after
[`cloud-itonami-isic-3311`](https://github.com/cloud-itonami/cloud-itonami-isic-3311)
(Repair of Fabricated Metal Products) -- the closest structural analog:
also a coordination-only repair actor with a closed op-allowlist and
schedule-op auto-eligibility -- adapted to the three structurally
disjoint transport-equipment asset classes described above.

### Closed op-allowlist (4 ops, all `:effect :propose`)

| Op | Ask | Implementation |
|---|---|---|
| `:log-repair-record` | inspection/disassembly/repair/reassembly/test-line batch data logging | Normalizes and commits a patch onto the asset's ground-truth fields (concern resolution, etc.) and appends an immutable repair-record-log entry. Gated on `:asset-verified?` like every other op in this actor (see `Actuation` below) -- MAY auto-commit at phase 3. |
| `:schedule-repair-operation` | repair/overhaul scheduling proposal | Drafts a proposed schedule WINDOW (never a repair-equipment control command or a certification-authority decision). MAY auto-commit at phase 3 when the governor is clean -- see `Actuation` below. |
| `:flag-safety-concern` | surface a structural-integrity/airworthiness/seaworthiness concern | Drafts a safety-concern flag; ALWAYS escalates to a human, unconditionally. Once approved, `transport-equipment-repair.notify` sends the notice (mail + phone, mock only -- see `Actuation`) to the asset's repair-technician/shop-safety-officer contact roster. |
| `:coordinate-return-to-service` | completed-unit return-to-customer coordination | Drafts a LOGISTICS coordination proposal (pickup/delivery scheduling, customer notification) -- NEVER an airworthiness/seaworthiness/rail-safety-certification-authority sign-off. MAY auto-commit at phase 3 when the governor is clean. |

**Legal basis is data, not code** --
`src/transport_equipment_repair/facts.cljk`'s `catalog` is the
per-(jurisdiction, asset-class) EDN source-of-truth the governor checks
every `:schedule-repair-operation` and `:coordinate-return-to-service`
proposal against (JPN/USA/DEU x marine-vessel/aircraft/railway-rolling-
stock, 9 seeded pairs, verified via live web search before this catalog
was written; DEU stands in for the EU):

| Jurisdiction | Asset class | Post-repair inspection/certification-before-return-to-service legal basis |
|---|---|---|
| 🇯🇵 Japan | Marine vessel | 船舶安全法（昭和8年法律第11号）第5条第1項第3号（臨時検査 -- 構造・設備の主要な部分について改造又は修理を行ったときは臨時検査を受けなければならない） -- [e-Gov](https://laws.e-gov.go.jp/law/308AC0000000011) |
| 🇯🇵 Japan | Aircraft | 航空法（昭和27年法律第231号）第19条（大修理又は大改造を行った航空機は修理改造検査に合格し、又は認定事業場の確認主任者の確認を受けた後でなければ航空の用に供してはならない） -- [e-Gov](https://laws.e-gov.go.jp/law/327AC0000000231) |
| 🇯🇵 Japan | Railway rolling stock | 鉄道に関する技術上の基準を定める省令（平成13年国土交通省令第151号）第89条（車両の主要部分の検査義務） -- [e-Gov](https://laws.e-gov.go.jp/law/413M60000800151) |
| 🇺🇸 USA | Marine vessel | 46 CFR 2.01-15 (Vessel repairs) + 46 CFR 170.005 -- OCMI-approved repairs/alterations, OCMI may require inspection/testing before return to service -- [law.cornell.edu](https://www.law.cornell.edu/cfr/text/46/2.01-15) |
| 🇺🇸 USA | Aircraft | 14 CFR 43.5 (Approval for return to service after maintenance, preventive maintenance, rebuilding, or alteration) -- [ecfr.gov](https://www.ecfr.gov/current/title-14/chapter-I/subchapter-C/part-43/section-43.5) |
| 🇺🇸 USA | Railway rolling stock | 49 CFR Part 215.9 (Railroad Freight Car Safety Standards -- Movement of cars for repair) -- [ecfr.gov](https://www.ecfr.gov/current/title-49/subtitle-B/chapter-II/part-215) |
| 🇪🇺 EU (DEU proxy) | Marine vessel | Regulation (EC) No 391/2009 (common rules and standards for ship inspection and survey organisations) -- [eur-lex.europa.eu](https://eur-lex.europa.eu/eli/reg/2009/391/oj/eng) |
| 🇪🇺 EU (DEU proxy) | Aircraft | Commission Regulation (EU) No 1321/2014 Annex II ('Part-145'), 145.A.50 (Certificate of Release to Service) -- [easa.europa.eu](https://www.easa.europa.eu/en/downloads/13511/en) |
| 🇪🇺 EU (DEU proxy) | Railway rolling stock | Commission Implementing Regulation (EU) 2019/779 (ECM certification) -- [eur-lex.europa.eu](https://eur-lex.europa.eu/eli/reg_impl/2019/779/oj/eng) |

UNLIKE `fabricated-metal-repair.facts` (a flat `iso3 -> basis` map), this
catalog is keyed `iso3 -> asset-class -> basis-map` -- ships, aircraft
and railway rolling stock genuinely sit under structurally disjoint
regulatory regimes (classification-society/flag-administration survey;
airworthiness-authority release-to-service; rail-safety-authority
maintenance certification) even within the SAME jurisdiction, so citing
one combined law per jurisdiction would be dishonest. All NINE seeded
pairs are honestly `:qualitative` here -- every source is a PROCEDURAL
requirement (inspect/certify/release a repaired transport unit before it
returns to service) with no fixed numeric advance-notice-days count this
actor could independently verify.
`transport-equipment-repair.facts/notification-lead-insufficient?`
reports `:qualitative` for every covered pair rather than fabricating a
number. See `transport-equipment-repair.facts` ns docstring for the full
honesty discipline and citations.

**Governor -- six HARD checks, ALL un-overridable by human approval:**
unknown op (outside the closed 4-op allowlist), `:effect` not
`:propose`, forbidden action class (repair-equipment-control /
direct-actuation / airworthiness-certification-decision /
seaworthiness-certification-decision / rail-safety-certification-
decision / return-to-service-sign-off markers), asset not independently
verified/registered (applied to ALL FOUR ops -- broader than
`cloud-itonami-isic-3311`'s equivalent check, which exempts
`:log-repair-record`; a genuine, documented domain-design requirement
that a repair-order/asset record be verified/registered BEFORE ANY
action), legal-basis missing (for `:schedule-repair-operation` AND
`:coordinate-return-to-service`), unresolved safety concern (for
`:schedule-repair-operation` AND `:coordinate-return-to-service`). See
`transport-equipment-repair.governor` ns docstring for the full
enumeration, rationale and real-law citations behind each.

## Actuation

This actor performs **no real-world actuation** -- every committed
record carries `:effect :propose` (see `transport-equipment-repair.
governor` ns docstring). `:flag-safety-concern` NEVER auto-commits at
any phase -- it always needs a human sign-off, even when the governor is
completely clean (`transport-equipment-repair.phase` ns docstring
'Actuation' section, `transport-equipment-repair.governor`'s
`high-stakes` set).

**Like `cloud-itonami-isic-3311`'s/`cloud-itonami-isic-3314`'s/
`cloud-itonami-isic-3319`'s `:schedule-repair-operation`, this actor's
`:schedule-repair-operation` AND `:coordinate-return-to-service` MAY
auto-commit at phase 3** when the governor is clean (asset independently
verified, legal-basis on file, no unresolved safety concern). Both ops
are still only ever a proposed diagnostic/repair/testing WINDOW or a
proposed completed-unit return-to-customer LOGISTICS coordination
(pickup/delivery scheduling, customer notification), never a live-work
authorization and never an airworthiness/seaworthiness/rail-safety-
certification-authority sign-off -- this actor's own HARD checks
(asset-verification, legal-basis-on-file, no-unresolved-concern) PLUS
its forbidden-action-class block on every certification-authority-
decision marker (including `:return-to-service-sign-off?`) already gate
the real hazard surface independently of phase. `:log-repair-record`
(data logging on an already-verified asset) also MAY auto-commit at
phase 3 when the governor is clean.

This build also deliberately ships **NO JVM-only interop anywhere in
`src/`** -- `transport-equipment-repair.notify` ships only the
deterministic mock `Notifier` (no real Resend/Twilio transport), per
this workspace's cljs-first `.cljc` runtime-priority rule. A real
transport can be added later behind the same protocol via a portable
HTTP client without changing this actor's shape.

```bash
clojure -M:dev:run    # demo: full coordination episode + every HARD hold
clojure -M:dev:test   # test suite
clojure -M:test       # test suite (without the local langchain-clj dev override)
clojure -M:lint       # clj-kondo, errors fail
```

## License

AGPL-3.0-or-later.
