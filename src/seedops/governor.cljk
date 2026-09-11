(ns seedops.governor
  "Seed Processing Governor -- the independent compliance layer that earns
  the SeedOpsAdvisor the right to commit. The LLM has no notion of:
    - Whether a batch's finished seed-lot moisture stayed within its safe
      storage window
    - Whether the batch's germination rate falls below the seed-lot
      type's minimum required rate
    - Whether physical/analytic purity falls within the seed-lot type's
      minimum required window
    - Whether other-crop-seed contamination exceeds the seed-lot type's
      maximum tolerance
    - Whether seed-borne disease (fungal/bacterial/viral pathogen
      screening) was detected in the batch
    - Whether the germination-testing equipment's (incubator/germinator/
      seed-counter) calibration is current
    - Whether final package weight variance is acceptable
    - Whether trait labeling (esp. GM-trait adventitious presence from a
      neighboring field's cross-pollination) is complete and accurate
    - Whether facility sanitation/cross-contamination-control score is
      passed
    - Whether an open quality concern has been resolved

  This MUST be a separate system able to *reject* a proposal and fall back
  to HOLD.

  Unlike direct cleaning/grading-equipment control (NEVER done by this
  actor -- scalper, gravity table, screen, and germinator operation
  remain exclusive to facility staff), the Governor operates on batch
  metadata: provenance, processing parameters, sanitation records, and
  quality flags. This is facility-operations coordination, not process
  control, and it is not seed-certification authority: this actor
  coordinates the paperwork and workflow around certification, it does
  not itself certify a seed lot for propagation.

  CRITICAL: Any proposal involving a quality concern (low germination,
  seed-borne disease, trait mislabeling) ALWAYS escalates to human
  facility-operator sign-off. The LLM's confidence is never sufficient
  for seed-viability or varietal-integrity decisions.

  Hard violations (always HOLD, no override):
    1. No jurisdiction citation (jurisdiction unknown -> can't verify reqs)
    2. Evidence incomplete (missing required-evidence per jurisdiction)
    3. Moisture out of target range (storage/viability safety)
    4. Germination rate below the seed-lot type's minimum required rate
    5. Purity below the seed-lot type's minimum required window
    6. Other-crop-seed contamination exceeds the seed-lot type's tolerance
    7. Seed-borne disease detected (pathogen screening)
    8. Germinator/incubator calibration overdue
    9. Weight variance excessive (packaging scale drift risk)
   10. Trait labeling mismatch (varietal-integrity / legal-compliance
       violation)
   11. Facility sanitation/cross-contamination-control score insufficient
   12. Quality flag unresolved (open concern, escalate required)

  Soft gates (always escalate for human):
    - Low confidence
    - Real actuation (`:log-processing-batch`, `:coordinate-shipment`)
    - `:flag-quality-concern` (never auto-resolved by confidence alone)

  This design mirrors `millops.governor` but specializes on
  seed-processing-for-propagation-specific concerns: germination-rate
  viability, varietal/physical purity, other-crop-seed contamination,
  and germination-testing-equipment calibration -- rather than
  food-safety mycotoxin contamination or milling-quality grading."
  (:require [seedops.facts :as facts]
            [seedops.registry :as registry]
            [seedops.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Logging a batch into processing records (`:log-processing-batch`) and
  coordinating shipment of certified seed (`:coordinate-shipment`) are
  the two real-world actuation events this actor performs. Both require
  facility operator sign-off."
  #{:log-processing-batch :coordinate-shipment})

(def always-escalate-ops
  "Operations that always require human sign-off, even when the Governor's
  hard checks are clean and confidence is high: the two high-stakes
  actuation events (`high-stakes`) plus `:flag-quality-concern` -- a
  quality concern (low germination rate, seed-borne disease, trait
  mislabeling) is never auto-resolved by advisor confidence alone, it
  always needs a human look."
  (conj high-stakes :flag-quality-concern))

(def allowed-ops
  "Closed allowlist of proposal operations this actor may ever make. Any
  proposal for an operation outside this set -- most importantly direct
  cleaning/grading-equipment control (scalper/gravity table/screen/
  germinator operation) or seed-certification-authority decisions -- is a
  hard, permanent block: this actor coordinates facility operations, it
  does not operate equipment and it does not certify seed for
  propagation."
  #{:log-processing-batch :schedule-maintenance :flag-quality-concern :coordinate-shipment})

;; ────────────────────────── Checks ──────────────────────────

(defn- op-not-allowed-violations
  "HARD, permanent block: any proposal outside the closed operation
  allowlist (e.g. direct cleaning/grading-equipment control, or a
  seed-certification-authority action) is refused unconditionally --
  this actor has no authority to make such a proposal at all, let alone
  commit it."
  [{:keys [op]} _proposal]
  (when-not (contains? allowed-ops op)
    [{:rule :op-not-allowed
      :detail (str op " はこのactorの許可された提案種別 (log-processing-batch/"
                  "schedule-maintenance/flag-quality-concern/coordinate-shipment) "
                  "に含まれない -- 洗浄/選別機制御やseed-certification認証権限はこのactorに無い")}]))

(defn- effect-not-propose-violations
  "HARD invariant: this actor's proposals are always `:effect :propose` --
  it never claims direct write/actuation authority for itself. A proposal
  asserting any other effect is refused unconditionally."
  [_request proposal]
  (when-let [effect (:effect proposal)]
    (when (not= effect :propose)
      [{:rule :effect-not-propose
        :detail (str "この actor の提案は :propose 以外の :effect を持てない (got " effect ")")}])))

(defn- shipment-batch-not-registered-violations
  "HARD invariant: a facility/batch record must be verified/registered in
  the store before `:coordinate-shipment` can be proposed against it --
  coordinating shipment of a batch this facility never checked in is out
  of scope for this actor."
  [{:keys [op subject]} st]
  (when (= op :coordinate-shipment)
    (when-not (store/production-batch st subject)
      [{:rule :batch-not-registered
        :detail (str subject " は施設に登録されたバッチ記録が無い -- 出荷調整提案は進められない")}])))

(defn- spec-basis-violations
  "A proposal with no jurisdiction citation is a HARD violation -- never
  invent a jurisdiction's seed-quality requirements."
  [{:keys [op]} proposal]
  (when (contains?
         #{:log-processing-batch :coordinate-shipment :flag-quality-concern}
         op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :jurisdiction) (nil? (:jurisdiction value))))
        [{:rule :no-spec-basis
          :detail "公式仕様の引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:log-processing-batch`, verify the batch's evidence checklist is
  complete per jurisdiction requirements."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)]
      (when-not (and b
                     (facts/required-evidence-satisfied?
                      (:jurisdiction b)
                      (:evidence-checklist b)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(seed-lot-intake-record/cleaning-grading-log/germination-test/purity-test等)が充足していない状態での提案"}]))))

(defn- moisture-out-of-target-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify that the batch's
  finished seed-lot moisture falls within tolerance via
  `registry/moisture-out-of-target?`. Evaluated UNCONDITIONALLY."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)
          p (when b (facts/seed-lot-type-by-id (:seed-lot-type b)))]
      (when (and b p (:moisture-percent b)
                 (registry/moisture-out-of-target?
                  (:moisture-percent b)
                  (:moisture-target-percent p)
                  (:moisture-tolerance-percent p)))
        [{:rule :moisture-out-of-target
          :detail (str subject " の水分(" (:moisture-percent b)
                      "%)が目標範囲外 -- バッチ登録提案は進められない")}]))))

(defn- germination-rate-below-minimum-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify that the batch's
  germination rate meets the seed-lot type's minimum required rate via
  `registry/germination-rate-below-minimum?`. Evaluated
  UNCONDITIONALLY -- this is the single most serious viability hazard
  specific to seed processing for propagation."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)
          p (when b (facts/seed-lot-type-by-id (:seed-lot-type b)))]
      (when (and b p (:germination-percent b)
                 (registry/germination-rate-below-minimum?
                  (:germination-percent b)
                  (:germination-min-percent p)))
        [{:rule :germination-rate-below-minimum
          :detail (str subject " の発芽率(" (:germination-percent b)
                      "%)が最低要件(" (:germination-min-percent p)
                      "%)を下回る -- バッチ登録提案は進められない")}]))))

(defn- purity-below-minimum-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify that the batch's
  physical/analytic purity meets the seed-lot type's minimum required
  window via `registry/purity-below-minimum?`."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)
          p (when b (facts/seed-lot-type-by-id (:seed-lot-type b)))]
      (when (and b p (:purity-percent b)
                 (registry/purity-below-minimum?
                  (:purity-percent b)
                  (:purity-min-percent p)))
        [{:rule :purity-below-minimum
          :detail (str subject " の純度(" (:purity-percent b)
                      "%)が製品規格を下回る -- バッチ登録提案は進められない")}]))))

(defn- other-crop-seed-exceeded-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify that the batch's
  other-crop-seed contamination falls within the seed-lot type's expected
  range via `registry/other-crop-seed-exceeded?`."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)
          p (when b (facts/seed-lot-type-by-id (:seed-lot-type b)))]
      (when (and b p (:other-crop-seed-percent b)
                 (registry/other-crop-seed-exceeded?
                  (:other-crop-seed-percent b)
                  (:other-crop-seed-max-percent p)))
        [{:rule :other-crop-seed-exceeded
          :detail (str subject " の他作物種子混入率(" (:other-crop-seed-percent b)
                      "%)が製品規格範囲外 -- バッチ登録提案は進められない")}]))))

(defn- seed-borne-disease-detected-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify the batch's own
  seed-borne-disease-screening result via `registry/seed-borne-disease-
  detected?`. A detection on THIS batch's own testing is a hard,
  future-crop hazard block -- distinct from `quality-flag-unresolved-
  violations` below, which covers a separately-raised, not-yet-resolved
  concern."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (registry/seed-borne-disease-detected? (:seed-borne-disease-detected? b)))
        [{:rule :seed-borne-disease-detected
          :detail (str subject " で種子伝染性病害(病原体)が検出された -- バッチ登録提案は進められない")}]))))

(defn- now-epoch-ms
  "Current time in epoch milliseconds, portable across Clojure/
  ClojureScript. Isolated to this single call site so the rest of the
  namespace (and all of `seedops.registry`) stays free of host-clock
  calls."
  []
  #?(:clj (System/currentTimeMillis)
     :cljs (js/Date.now)))

(defn- germinator-calibration-overdue-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify that the
  germination-testing equipment's calibration is current (recalibration
  required every 60 days)."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (:germinator-last-calibration-date b)
                 (registry/germinator-calibration-overdue? (:germinator-last-calibration-date b) (now-epoch-ms)))
        [{:rule :germinator-calibration-overdue
          :detail (str subject " の発芽試験機(ジャーミネーター)校正が期限切れ -- バッチ登録提案は進められない")}]))))

(defn- weight-variance-excessive-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify the weight variance."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (:weight-variance-grams b)
                 (registry/weight-variance-excessive? (:weight-variance-grams b) 50))
        [{:rule :weight-variance-excessive
          :detail (str subject " の重量分散(" (:weight-variance-grams b)
                      "g)が許容範囲(50g)を超過 -- バッチ登録提案は進められない")}]))))

(defn- trait-label-mismatch-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify trait declaration
  completeness and accuracy via `registry/trait-label-risk?`."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)
          formula-traits (facts/seed-source-trait-set (:seed-sources b))]
      (when (and b formula-traits (:declared-traits b)
                 (registry/trait-label-risk? formula-traits (:declared-traits b)))
        [{:rule :trait-label-mismatch
          :detail (str subject " の形質表示が不完全 -- バッチ登録提案は進められない")}]))))

(defn- sanitation-score-insufficient-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify that the facility's
  sanitation/cross-contamination-control score meets minimum
  requirements."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (:sanitation-score b)
                 (registry/sanitation-score-insufficient? (:sanitation-score b) 75))
        [{:rule :sanitation-score-insufficient
          :detail (str subject " の施設衛生/交差汚染防止スコア(" (:sanitation-score b)
                      ")が最低要件(75)を下回る -- バッチ登録提案は進められない")}]))))

(defn- quality-flag-unresolved-violations
  "An unresolved quality flag is a HARD, un-overridable hold. Quality
  concerns (suspected low germination, seed-borne disease, trait
  mislabeling) raised during processing or testing MUST be resolved
  before the batch can be logged. Evaluated UNCONDITIONALLY at
  `:log-processing-batch`."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)]
      (when (and (true? (:quality-concern-raised? b))
                 (not (true? (:quality-concern-resolved? b))))
        [{:rule :quality-flag-unresolved
          :detail (str subject " は未解決の品質フラグがある -- バッチ登録提案は進められない")}]))))

(defn- already-processed-violations
  "For `:log-processing-batch`, refuse to process the SAME batch twice, off
  a dedicated `:processed?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (when (store/batch-already-processed? st subject)
      [{:rule :already-processed
        :detail (str subject " は既に登録済み")}])))

(defn- already-shipment-finalized-violations
  "For `:coordinate-shipment`, refuse to finalize the SAME batch's shipment
  twice, off a dedicated `:shipment-finalized?` fact."
  [{:keys [op subject]} st]
  (when (= op :coordinate-shipment)
    (when (store/batch-shipment-finalized? st subject)
      [{:rule :already-shipment-finalized
        :detail (str subject " は既に出荷確定済み")}])))

(defn check
  "Censors a SeedOpsAdvisor proposal against the Governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}.

  Stakes (high-stakes actuation vs. always-escalate) are read off the
  REQUEST's `:op` -- not off the proposal -- since the operation being
  proposed (not the advisor's self-reported stake) is what determines
  whether a human must sign off."
  [request _context proposal st]
  (let [hard (into []
                   (concat (op-not-allowed-violations request proposal)
                           (effect-not-propose-violations request proposal)
                           (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (moisture-out-of-target-violations request st)
                           (germination-rate-below-minimum-violations request st)
                           (purity-below-minimum-violations request st)
                           (other-crop-seed-exceeded-violations request st)
                           (seed-borne-disease-detected-violations request st)
                           (germinator-calibration-overdue-violations request st)
                           (weight-variance-excessive-violations request st)
                           (trait-label-mismatch-violations request st)
                           (sanitation-score-insufficient-violations request st)
                           (quality-flag-unresolved-violations request st)
                           (already-processed-violations request st)
                           (already-shipment-finalized-violations request st)
                           (shipment-batch-not-registered-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        actuation? (boolean (high-stakes (:op request)))
        escalate-op? (boolean (always-escalate-ops (:op request)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not escalate-op?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? escalate-op?))
     :high-stakes? actuation?}))

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
