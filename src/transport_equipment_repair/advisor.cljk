(ns transport-equipment-repair.advisor
  "Repair Advisor client -- the *contained intelligence node* for the
  transport-equipment-repair OPERATIONS COORDINATION actor.

  It normalizes repair-record logging (inspection/disassembly/repair/
  reassembly/test-line data for ships/boats, aircraft/spacecraft and
  railway locomotives/rolling stock), drafts a diagnostic/repair/testing
  SCHEDULE PROPOSAL (never a repair-equipment control command or an
  airworthiness/seaworthiness/rail-safety-certification-authority
  decision), drafts a safety-concern FLAG (structural-integrity/
  airworthiness/seaworthiness/rail-safety concern), and drafts a
  completed-unit return-to-customer COORDINATION PROPOSAL (pickup/
  delivery scheduling, customer notification -- never a certification-
  authority sign-off). CRITICAL: it is a smart-but-untrusted advisor, and
  it is SCOPED -- it holds NO repair-equipment control authority and NO
  airworthiness/seaworthiness/rail-safety-certification-authority (that
  is the classification society's/airworthiness authority's/rail-safety
  authority's exclusively). It returns a *proposal* (with a rationale +
  the fields it cited), NEVER a committed record, a real mail/phone
  send, or a real certification-authority sign-off. Every output carries
  `:effect :propose` and is censored downstream by `transport-equipment-
  repair.governor` before anything touches the SSoT -- see README
  `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the legal-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- see `transport-equipment-repair.governor`
     :value      map            ; the coordination-artifact payload
     :stake      kw|nil         ; the op itself (drives high-stakes gating) or nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [kotoba.lang.text :as str]
            [transport-equipment-repair.facts :as facts]
            [transport-equipment-repair.store :as store]
            [langchain.model :as model]))

(defn- log-repair-record
  "Repair-record-log normalization -- the LLM only normalizes/validates
  the patch (inspection/disassembly/repair/reassembly/test-line finding
  data); it does not invent the asset, its jurisdiction, its asset class
  or its ground-truth fields. High confidence, low stakes -- `:stake
  nil`, the lowest-risk op in this actor's closed allowlist."
  [_db {:keys [patch]}]
  {:summary    (str "修理記録更新: " (pr-str (keys patch)))
   :rationale  "入力patchの正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :propose
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- schedule-repair-operation
  "Draft a diagnostic/repair/testing SCHEDULE PROPOSAL -- a proposed
  operation window (never a repair-equipment control command or an
  airworthiness/seaworthiness/rail-safety-certification-authority
  decision; see `transport-equipment-repair.governor` ns docstring).
  `:stake :schedule-repair-operation`."
  [db {:keys [subject window notes]}]
  (let [a (store/asset db subject)
        iso3 (:jurisdiction a)
        asset-class (:asset-class a)
        sb (facts/spec-basis iso3 asset-class)]
    (if (nil? sb)
      {:summary    (str iso3 "/" asset-class " の公式legal-basisが見つかりません -- スケジュール提案不可")
       :rationale  "transport-equipment-repair.facts に未登録の(jurisdiction, asset-class)。要件を推測で作らない。"
       :cites      []
       :effect     :propose
       :value      {:asset-id subject :jurisdiction iso3 :asset-class asset-class :window window :notes notes :spec-basis nil}
       :stake      :schedule-repair-operation
       :confidence 0.2}
      {:summary    (str subject " 向け輸送用機器修理作業スケジュール提案 (" (:owner-authority sb) ")"
                        (when a (str " (asset=" (:name a) ")")))
       :rationale  (str "修理後の再稼働前検査basis: " (:repair-safety-basis sb)
                       " / asset-verified?=" (:asset-verified? a)
                       " / safety-concern-unresolved?=" (:safety-concern-unresolved? a))
       :cites      [(:repair-safety-basis sb) (:repair-safety-provenance sb)]
       :effect     :propose
       :value      {:asset-id subject :jurisdiction iso3 :asset-class asset-class :window window :notes notes
                    :spec-basis (:repair-safety-provenance sb)}
       :stake      :schedule-repair-operation
       :confidence (if (and a (:asset-verified? a) (not (:safety-concern-unresolved? a))) 0.9 0.3)})))

(defn- flag-safety-concern
  "Draft a SAFETY-CONCERN FLAG -- surfacing a structural-integrity/
  airworthiness/seaworthiness/rail-safety concern for human review.
  `:stake :flag-safety-concern` -- ALWAYS escalates to a human,
  unconditionally, regardless of confidence (see README `Actuation` +
  `transport-equipment-repair.governor` ns docstring `high-stakes`)."
  [db {:keys [subject concern-type concern-description]}]
  (let [a (store/asset db subject)
        sb (facts/spec-basis (:jurisdiction a) (:asset-class a))]
    {:summary    (str subject ": 安全性懸念を検出（" (name (or concern-type :structural-integrity-failure)) "）"
                      (when a (str " (asset=" (:name a) ")")))
     :rationale  (if sb
                   (str "関連basis: " (:repair-safety-basis sb))
                   "現有記録または(jurisdiction, asset-class) spec-basisが見つかりません")
     :cites      (if sb [(:repair-safety-basis sb)] [])
     :effect     :propose
     :value      {:asset-id subject
                  :concern-type (or concern-type :structural-integrity-failure)
                  :concern-description concern-description
                  :subject-line (str "[至急] " (:name a) " 安全性懸念のお知らせ")
                  :body (str (:name a) "について安全性懸念（" (name (or concern-type :structural-integrity-failure))
                            "）が検出されました。詳細を確認し対応を検討してください。")
                  :message (str (:name a) "、安全性懸念が検出されました。至急ご確認ください。")}
     :stake      :flag-safety-concern
     :confidence 0.9}))

(defn- coordinate-return-to-service
  "Draft a completed-unit return-to-customer LOGISTICS COORDINATION
  PROPOSAL (pickup/delivery scheduling, customer notification) -- NEVER
  an airworthiness/seaworthiness/rail-safety-certification-authority
  sign-off (see `transport-equipment-repair.governor` ns docstring
  forbidden-action-class check). `:stake :coordinate-return-to-service`."
  [db {:keys [subject window notes]}]
  (let [a (store/asset db subject)
        iso3 (:jurisdiction a)
        asset-class (:asset-class a)
        sb (facts/spec-basis iso3 asset-class)]
    (if (nil? sb)
      {:summary    (str iso3 "/" asset-class " の公式legal-basisが見つかりません -- 稼働再開調整提案不可")
       :rationale  "transport-equipment-repair.facts に未登録の(jurisdiction, asset-class)。要件を推測で作らない。"
       :cites      []
       :effect     :propose
       :value      {:asset-id subject :jurisdiction iso3 :asset-class asset-class :window window :notes notes :spec-basis nil}
       :stake      :coordinate-return-to-service
       :confidence 0.2}
      {:summary    (str subject " 向け完了ユニット返却（customer返却）ロジスティクス調整提案 (" (:owner-authority sb) ")"
                        (when a (str " (asset=" (:name a) ")")))
       :rationale  (str "返却前提要件basis: " (:repair-safety-basis sb)
                       " / asset-verified?=" (:asset-verified? a)
                       " / safety-concern-unresolved?=" (:safety-concern-unresolved? a)
                       " -- このアクターはcertification authorityのサインオフを一切行わない、customerへの物流調整のみ")
       :cites      [(:repair-safety-basis sb) (:repair-safety-provenance sb)]
       :effect     :propose
       :value      {:asset-id subject :jurisdiction iso3 :asset-class asset-class :window window :notes notes
                    :spec-basis (:repair-safety-provenance sb)}
       :stake      :coordinate-return-to-service
       :confidence (if (and a (:asset-verified? a) (not (:safety-concern-unresolved? a))) 0.9 0.3)})))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :log-repair-record            (log-repair-record db request)
    :schedule-repair-operation    (schedule-repair-operation db request)
    :flag-safety-concern          (flag-safety-concern db request)
    :coordinate-return-to-service (coordinate-return-to-service db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :propose :value {} :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは輸送用機器（船舶、航空機、鉄道車両）修理工房の運行調整"
       "（オペレーションズ・コーディネーション）エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) :effect(常に:propose) "
       ":value(提案内容のmap) "
       ":stake(:log-repair-record|:schedule-repair-operation|:flag-safety-concern|"
       ":coordinate-return-to-service のいずれか) :confidence(0..1)。\n"
       "重要: あなたは修理設備を直接操作するコマンドや、耐空性/堪航性/鉄道安全"
       "certification authorityの決定（返品・稼働再開return-to-serviceの確定を"
       "含む）を伴う提案を絶対に作成してはいけません（classification society/"
       "airworthiness authority/rail-safety authorityの専権事項）。登録されて"
       "いない(jurisdiction, asset-class)の要件を絶対に創作してはいけません。"
       "legal-basisが無い場合は:citesを空にしconfidenceを上げないこと。"))

(defn- facts-for [st {:keys [subject]}]
  {:asset (store/asset st subject)})

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Repair Governor
  escalates/holds -- an LLM hiccup can never auto-schedule an operation,
  auto-flag (or suppress) a safety concern, or auto-coordinate a
  return-to-service."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :propose))
          (update :value #(or % {})))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :propose :value {} :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :advisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
