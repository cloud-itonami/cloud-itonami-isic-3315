(ns transport-equipment-repair.facts
  "Per-jurisdiction, per-asset-class post-repair inspection/certification-
  before-return-to-service regulatory catalog -- the spec-basis table the
  Repair Governor checks every `:schedule-repair-operation` AND
  `:coordinate-return-to-service` proposal against ('did the advisor cite
  an OFFICIAL public source for this jurisdiction's post-repair
  inspection/certification duty for THIS asset class, or did it invent
  one?'). Same honest-coverage discipline `installation.facts`/
  `demolition.facts`/`construction.facts`/`fabricated-metal-repair.
  facts`/`electrical-equipment-repair.facts`/`other-equipment-repair.
  facts` established for this fleet: a (jurisdiction, asset-class) pair
  not in this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  UNLIKE `fabricated-metal-repair.facts` (a single flat iso3 -> basis
  map), ISIC 3315 genuinely spans THREE structurally disjoint regulatory
  regimes under one repair-shop-coordination shape -- ships/boats
  (classification-society/flag-administration survey), aircraft/
  spacecraft (airworthiness authority release-to-service) and railway
  locomotives/rolling stock (rail-safety-authority maintenance
  certification) -- each with its OWN owner-authority and its OWN
  official source, even within the SAME jurisdiction. Citing one
  combined 'transport equipment' law per jurisdiction would be dishonest
  (no such single law exists in any of the three researched
  jurisdictions), so this catalog is keyed `iso3 -> asset-class ->
  basis-map` instead. `spec-basis` therefore takes BOTH `iso3` and
  `asset-class` (`:marine-vessel` | `:aircraft` | `:railway-rolling-
  stock`).

  Coverage is reported HONESTLY (see `coverage`); this is a STARTING
  catalog (JPN/USA/DEU x 3 asset classes = 9 seeded entries), not a
  from-scratch survey of all ~194 jurisdictions x every transport-
  equipment sub-type. Extending coverage is additive: add one map to
  `catalog`, cite a real source, done -- never invent a jurisdiction's or
  asset class's requirements to make coverage look bigger.

  Real sources, verified via live web search before this catalog was
  written (no fabrication):

  MARINE (ships/boats):
    JPN -- 船舶安全法（昭和8年法律第11号）第5条第1項第3号: a vessel that
           undergoes a 国土交通省令で定める改造又は修理（構造・設備等の
           主要な部分に係るもの）must undergo a 臨時検査（extraordinary/
           temporary survey）before it may resume operating under its
           existing 船舶検査証書 -- https://laws.e-gov.go.jp/law/308AC0000000011
    USA -- 46 CFR §2.01-15 (Vessel repairs) + §170.005 (Vessel alteration
           or repair): repairs or alterations affecting the safety of an
           inspected vessel or its machinery may not be made without the
           applicable requirements being met, and the cognizant OCMI
           (Officer in Charge, Marine Inspection, U.S. Coast Guard) may
           require inspection and testing before the vessel returns to
           service -- https://www.law.cornell.edu/cfr/text/46/2.01-15
    DEU -- Regulation (EC) No 391/2009 (common rules and standards for
           ship inspection and survey organisations): a Recognised
           Organisation (classification society, e.g. DNV, acting on
           behalf of an EU flag administration) conducts the statutory
           survey/certification work; recognised organisations must
           exchange information on ships changing class specifically to
           avoid carrying out necessary repairs, closing that evasion
           route -- https://eur-lex.europa.eu/eli/reg/2009/391/oj/eng
           (EU jurisdiction proxy, same convention this fleet's other
           DEU/EU entries use -- no ISO-3166 alpha-3 code for the EU
           itself; this Regulation is directly applicable EU law, no
           German transposition needed for the citation itself)

  AVIATION (aircraft/spacecraft):
    JPN -- 航空法（昭和27年法律第231号）第19条: an aircraft that has
           undergone a 大修理 or 大改造 (major repair/major alteration)
           may not be flown until it has passed a 修理改造検査 by the
           Minister of MLIT (航空機検査官) -- or, at a MLIT-certificated
           repair/alteration station, until the station's own 確認主任者
           has confirmed conformity in lieu of the government inspection
           -- https://laws.e-gov.go.jp/law/327AC0000000231
    USA -- 14 CFR §43.5 (Approval for return to service after
           maintenance, preventive maintenance, rebuilding, or
           alteration): no person may approve an aircraft, airframe,
           engine, propeller or appliance for return to service after
           maintenance/repair/alteration unless the §43.9/§43.11
           maintenance-record entry has been made and any required
           repair/alteration form has been executed as prescribed --
           https://www.ecfr.gov/current/title-14/chapter-I/subchapter-C/part-43/section-43.5
    DEU -- Commission Regulation (EU) No 1321/2014 Annex II ('Part-145'),
           145.A.50 (Certification of maintenance): an EASA-approved
           Part-145 maintenance organisation must issue a Certificate of
           Release to Service before flight, once appropriately
           authorised certifying staff have verified all ordered
           maintenance/repair was properly carried out and no
           non-compliance endangering flight safety is known --
           https://www.easa.europa.eu/en/downloads/13511/en (EU
           jurisdiction proxy, same convention as marine above)

  RAIL (railway locomotives and rolling stock):
    JPN -- 鉄道に関する技術上の基準を定める省令（平成13年国土交通省令
           第151号）第89条: a railway operator must inspect the major
           parts of rolling stock according to its type and operating
           condition (種類及び運行状況に応じ、車両の主要部分の検査を
           行わなければならない) -- the periodic/post-repair inspection
           duty this catalog cites for the rail mode --
           https://laws.e-gov.go.jp/law/413M60000800151
    USA -- 49 CFR Part 215 (Railroad Freight Car Safety Standards),
           §215.9 (Movement of cars for repair): a freight car found
           defective under this Part may only be moved to a repair
           location under prescribed conditions (tagged/inspected by a
           designated Part-215 inspector), and loses that movement
           privilege -- with full Part-215 liability attaching -- if
           those conditions aren't observed, structurally tying
           continued movement/return to unrestricted service to the
           defect actually being corrected --
           https://www.ecfr.gov/current/title-49/subtitle-B/chapter-II/part-215
    DEU -- Commission Implementing Regulation (EU) 2019/779 (system of
           certification of entities in charge of maintenance -- 'ECM'):
           a certified ECM must use a standardised maintenance-management
           system to ensure vehicles are in a safe operating state,
           serviced according to vehicle-specific documentation, before
           being released back into service --
           https://eur-lex.europa.eu/eli/reg_impl/2019/779/oj/eng (EU
           jurisdiction proxy, same convention as marine/aviation above)

  Every one of these NINE seeded entries is honestly `:qualitative` --
  every source imposes a PROCEDURAL inspection/certification/release-to-
  service duty, not a fixed numeric advance-notice-days count. This actor
  does NOT invent one where none of its nine researched (jurisdiction,
  asset-class) sources gives one, the same honest-coverage discipline
  `fabricated-metal-repair.facts` established for the sibling repair
  actor -- see that ns docstring for why this is a genuine, honest
  research finding and not an oversight."
  )

(def catalog
  "iso3 -> asset-class -> requirement map. `:repair-safety-basis` / its
  `-provenance`, plus `:owner-authority`, are the citation the governor
  requires before a `:schedule-repair-operation` or `:coordinate-return-
  to-service` proposal can ever commit -- see ns docstring."
  {"JPN"
   {:marine-vessel
    {:name "Japan" :asset-class :marine-vessel
     :owner-authority "国土交通大臣（地方運輸局長）／日本小型船舶検査機構(JCI)等の指定検査機関"
     :repair-safety-basis "船舶安全法（昭和8年法律第11号）第5条第1項第3号（臨時検査 -- 船体・機関等の構造・設備の主要な部分について国土交通省令で定める改造又は修理を行ったときは、既存の船舶検査証書のまま運航を継続する前に臨時検査を受けなければならない）"
     :repair-safety-provenance "https://laws.e-gov.go.jp/law/308AC0000000011"
     :threshold-model :qualitative
     :notification-lead-days nil
     :threshold-note "船舶安全法第5条第1項第3号は主要な改造・修理後の臨時検査を義務付けるが、固定日数の事前届出リードタイムは定めていない -- ここで数値を創作しない。"}
    :aircraft
    {:name "Japan" :asset-class :aircraft
     :owner-authority "国土交通大臣（航空機検査官）／認定事業場の確認主任者"
     :repair-safety-basis "航空法（昭和27年法律第231号）第19条（大修理又は大改造を行った航空機は、修理改造検査に合格し、又は認定事業場の確認主任者の確認を受けた後でなければ航空の用に供してはならない）"
     :repair-safety-provenance "https://laws.e-gov.go.jp/law/327AC0000000231"
     :threshold-model :qualitative
     :notification-lead-days nil
     :threshold-note "航空法第19条は大修理・大改造後の検査合格（又は認定事業場確認）を義務付けるが、固定日数の事前届出リードタイムは定めていない -- ここで数値を創作しない。"}
    :railway-rolling-stock
    {:name "Japan" :asset-class :railway-rolling-stock
     :owner-authority "国土交通大臣／鉄道事業者"
     :repair-safety-basis "鉄道に関する技術上の基準を定める省令（平成13年国土交通省令第151号）第89条（車両は、その種類及び運行状況に応じ、車両の主要部分の検査を行わなければならない -- 修理後の使用再開前の検査もこれに含まれる）"
     :repair-safety-provenance "https://laws.e-gov.go.jp/law/413M60000800151"
     :threshold-model :qualitative
     :notification-lead-days nil
     :threshold-note "鉄道に関する技術上の基準を定める省令第89条は車両主要部分の検査義務を定めるが、固定日数の事前届出リードタイムは定めていない -- ここで数値を創作しない。"}}
   "USA"
   {:marine-vessel
    {:name "United States" :asset-class :marine-vessel
     :owner-authority "U.S. Coast Guard, Officer in Charge, Marine Inspection (OCMI)"
     :repair-safety-basis "46 CFR 2.01-15 (Vessel repairs) + 46 CFR 170.005 (Vessel alteration or repair): repairs or alterations affecting the safety of an inspected vessel or its machinery may not be made without the applicable requirements being met, and the cognizant OCMI may require inspection and testing before the vessel returns to service."
     :repair-safety-provenance "https://www.law.cornell.edu/cfr/text/46/2.01-15"
     :threshold-model :qualitative
     :notification-lead-days nil
     :threshold-note "46 CFR 2.01-15/170.005 require OCMI-approved repairs/alterations and permit an OCMI-ordered inspection/test before return to service, but set no fixed nationwide advance-notice-days count -- this actor does not invent one."}
    :aircraft
    {:name "United States" :asset-class :aircraft
     :owner-authority "FAA-certificated mechanic / repair station / Inspection Authorization (IA) holder"
     :repair-safety-basis "14 CFR 43.5 (Approval for return to service after maintenance, preventive maintenance, rebuilding, or alteration): no person may approve an aircraft, airframe, engine, propeller or appliance for return to service after maintenance/repair/alteration unless the 43.9/43.11 maintenance-record entry has been made and any required repair/alteration form has been executed as prescribed."
     :repair-safety-provenance "https://www.ecfr.gov/current/title-14/chapter-I/subchapter-C/part-43/section-43.5"
     :threshold-model :qualitative
     :notification-lead-days nil
     :threshold-note "14 CFR 43.5 requires a documented approval-for-return-to-service before flight, but sets no fixed nationwide advance-notice-days count -- this actor does not invent one."}
    :railway-rolling-stock
    {:name "United States" :asset-class :railway-rolling-stock
     :owner-authority "Federal Railroad Administration (FRA)"
     :repair-safety-basis "49 CFR Part 215 (Railroad Freight Car Safety Standards), 215.9 (Movement of cars for repair): a freight car found defective under this Part may only be moved to a repair location under prescribed conditions (tagged/inspected by a designated Part-215 inspector), tying continued/unrestricted movement to the defect actually being corrected."
     :repair-safety-provenance "https://www.ecfr.gov/current/title-49/subtitle-B/chapter-II/part-215"
     :threshold-model :qualitative
     :notification-lead-days nil
     :threshold-note "49 CFR Part 215.9 conditions continued movement/return to unrestricted service on defect correction, but sets no fixed nationwide advance-notice-days count -- this actor does not invent one."}}
   "DEU"
   {:marine-vessel
    {:name "Germany (EU jurisdiction proxy, see ns docstring)" :asset-class :marine-vessel
     :owner-authority "EU-recognised classification society (Recognised Organisation) acting on behalf of the flag administration"
     :repair-safety-basis "Regulation (EC) No 391/2009 (common rules and standards for ship inspection and survey organisations): a Recognised Organisation conducts the statutory survey/certification work and must exchange information on ships changing class specifically to avoid carrying out necessary repairs, closing that evasion route."
     :repair-safety-provenance "https://eur-lex.europa.eu/eli/reg/2009/391/oj/eng"
     :threshold-model :qualitative
     :notification-lead-days nil
     :threshold-note "Regulation (EC) No 391/2009 grounds the Recognised-Organisation survey/certification duty but sets no fixed advance-notice-days count for a repair itself -- this actor does not invent one."}
    :aircraft
    {:name "Germany (EU jurisdiction proxy, see ns docstring)" :asset-class :aircraft
     :owner-authority "EASA-approved Part-145 maintenance organisation (certifying staff)"
     :repair-safety-basis "Commission Regulation (EU) No 1321/2014 Annex II ('Part-145'), 145.A.50 (Certification of maintenance): a Certificate of Release to Service must be issued before flight at completion of any maintenance/repair, once authorised certifying staff have verified the work was properly carried out and no non-compliance endangering flight safety is known."
     :repair-safety-provenance "https://www.easa.europa.eu/en/downloads/13511/en"
     :threshold-model :qualitative
     :notification-lead-days nil
     :threshold-note "Part-145.A.50 requires a Certificate of Release to Service before flight, but sets no fixed advance-notice-days count -- this actor does not invent one."}
    :railway-rolling-stock
    {:name "Germany (EU jurisdiction proxy, see ns docstring)" :asset-class :railway-rolling-stock
     :owner-authority "certified Entity in Charge of Maintenance (ECM), under national safety authority oversight"
     :repair-safety-basis "Commission Implementing Regulation (EU) 2019/779 (system of certification of entities in charge of maintenance -- ECM): a certified ECM must use a standardised maintenance-management system to ensure a vehicle is in a safe operating state, serviced according to vehicle-specific documentation, before it is released back into service."
     :repair-safety-provenance "https://eur-lex.europa.eu/eli/reg_impl/2019/779/oj/eng"
     :threshold-model :qualitative
     :notification-lead-days nil
     :threshold-note "Regulation (EU) 2019/779 grounds the ECM release-to-service duty but sets no fixed advance-notice-days count -- this actor does not invent one."}}})

(defn spec-basis
  "The (jurisdiction, asset-class)'s requirement map, or nil -- nil means
  NO spec-basis, and the governor must hold any `:schedule-repair-
  operation` / `:coordinate-return-to-service` proposal that tries to
  cite one."
  [iso3 asset-class]
  (get-in catalog [iso3 asset-class]))

(defn coverage
  "Honest coverage report over (iso3, asset-class) pairs: how many of the
  requested pairs actually have a spec-basis entry. Never report a
  missing pair as covered."
  ([] (coverage (for [iso3 (keys catalog) asset-class (keys (get catalog iso3))] [iso3 asset-class])))
  ([pairs]
   (let [have (filter (fn [[iso3 asset-class]] (spec-basis iso3 asset-class)) pairs)
         missing (remove (fn [[iso3 asset-class]] (spec-basis iso3 asset-class)) pairs)]
     {:requested (count pairs)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-3315 R0: "
                 (reduce + (map count (vals catalog)))
                 " (jurisdiction, asset-class) pairs seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 jurisdictions x "
                 "every transport-equipment sub-type -- extend `transport-equipment-repair."
                 "facts/catalog`, never fabricate a jurisdiction's or asset class's requirements.")})))

(defn notification-lead-insufficient?
  "Independently recompute whether a (jurisdiction, asset-class) pair has
  a fixed numeric advance-notice-days requirement this actor could
  re-check. Three-valued, deliberately (the same shape `fabricated-
  metal-repair.facts/notification-lead-insufficient?` established):
    true/false   -- never produced by this catalog (see ns docstring):
                    none of the nine seeded pairs carries a
                    `:quantitative` threshold-model.
    :qualitative -- a pair with NO fixed numeric lead-time (every seeded
                    pair in this catalog). This actor cannot
                    independently confirm 'sufficient' or 'insufficient'
                    by arithmetic alone. Never fabricate a lead-time here.
    nil          -- no spec-basis at all for this (iso3, asset-class)
                    pair (not in `catalog`)."
  [iso3 asset-class _asset]
  (when-let [{:keys [threshold-model]} (spec-basis iso3 asset-class)]
    (case threshold-model
      :qualitative :qualitative
      nil)))
