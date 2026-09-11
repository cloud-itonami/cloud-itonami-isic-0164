(ns seedops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: it previously had no
  demo page and no generator. This namespace drives the REAL actor stack
  --- `seedops.operation/run-operation` -> `seedops.governor/check` ->
  `seedops.store` --- and renders whatever that stack actually returns.

  Why the scenario is authored here rather than reused: this repo's own
  demo driver (`clojure -M:dev:run`, i.e. `seedops.sim`) is still a stub
  (it prints \"SeedOps simulation: not yet implemented\" and exits), so
  there are no seeded batch ids to reuse. It was run before this file was
  written, to confirm exactly that. What DOES exist as seed data is
  `seedops.facts`: `seed-lot-types` (moisture window / germination floor /
  purity floor / other-crop-seed ceiling per seed-lot type),
  `jurisdictions` (required-evidence checklists, regulated traits) and
  `seed-source-trait-table` (per-cultivar regulated traits).

  So the rule this file follows is: **no free-typed domain values.**
  - every seed-lot-type id, jurisdiction id, seed-source id, evidence item
    and trait keyword on the page comes from `seedops.facts`;
  - every at-spec measurement is read straight off that seed-lot type's own
    published window (moisture at target, germination/purity at the
    certification floor, other-crop seed at the tolerance ceiling --- every
    Governor check is a strict inequality, so an at-spec lot passes);
  - every out-of-spec measurement is derived from that same window by one
    documented transform (see `spec-margin` / `build-batch`);
  - every hold reason and every hold detail string is the Governor's own
    output, copied out of the verdict at render time, never retyped.

  Batch ids (`batch-001` ...) follow the convention this repo's own tests
  already use (`seedops.store-test`, `seedops.operation-test`); the store is
  seeded with them directly, exactly as those tests seed `{:batches {...}}`.
  The `weight-variance-grams 20` / `sanitation-score 85` at-spec values are
  taken from this repo's own `seedops.operation-test/clean-batch` fixture,
  since those two Governor thresholds (50 g, 75) are literals inside
  `seedops.governor` rather than published facts.

  Determinism: the page contains no timestamp, no random value and no map
  iteration order --- every table is explicitly sorted. Germinator
  calibration dates are the one clock-derived input (the Governor compares
  them against `now`), so they are expressed as offsets from now (10 days =
  current, 90 days = past the 60-day limit) and are never rendered, which
  keeps both the bytes and the verdicts stable over time.

  Styling: the page carries the same デジタル庁デザインシステム (DADS) token layer
  as this repo's own product face. It is lifted at build time out of the copy
  already vendored inside `docs/index.html` (see `dads-token-layer`) rather than
  pulled from a `jp-go-dds` git coordinate, so the build needs no network and the
  console cannot drift away from the page it sits next to. Only the DADS
  primitives actually referenced are used, and `-main` fails the build if any
  `var(--x)` on the finished page resolves to nothing --- an unstyled console is
  a build error here, not a thing you find by looking at it.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.java.io :as io]
            [kotoba.lang.text :as str]
            [seedops.facts :as facts]
            [seedops.governor :as governor]
            [seedops.operation :as op]
            [seedops.store :as store]))

;; ─────────────────────────── actor wiring ───────────────────────────

(def ^:private actor-id "seedops-actor-1")

(def ^:private approver
  "The facility operator who signs off the escalated proposals in this run.
  `seedops.governor/always-escalate-ops` makes every actuation event
  (`:log-processing-batch`, `:coordinate-shipment`) and every
  `:flag-quality-concern` require a human, no matter how clean."
  "op-1")

(def ^:private context
  "The context `seedops.operation/run-operation` requires: an actor id and
  the hold-fact constructor it calls when a verdict is not `:ok?`."
  {:actor-id actor-id :hold-fact-fn governor/hold-fact})

(def ^:private day-ms (* 24 60 60 1000))

(defn- days-ago
  "Epoch-ms `n` days before now. Only ever fed to the Governor's calibration
  check --- never rendered --- so the page stays byte-identical while the
  verdict stays stable as real time passes."
  [n]
  (- (System/currentTimeMillis) (* n day-ms)))

(def ^:private spec-margin
  "The single scenario delta. Every out-of-spec measurement below is derived
  from the seed-lot type's own published limit in `seedops.facts` using this
  margin (or that lot's own tolerance/ceiling), so no failing measurement is
  a free-typed number either."
  6.0)

;; ──────────────────────────── seed batches ──────────────────────────

(defn- at-spec-batch
  "A batch whose measurements are read straight off the seed-lot type's own
  published window in `seedops.facts`, and whose evidence checklist is the
  jurisdiction's own `:required-evidence` list. Declared traits are exactly
  the traits the chosen seed sources actually carry, so trait labelling is
  accurate by construction."
  [lot-id jurisdiction-id seed-sources]
  (let [lot (facts/seed-lot-type-by-id lot-id)
        j   (facts/jurisdiction-by-id jurisdiction-id)]
    {:seed-lot-type                    lot-id
     :jurisdiction                     jurisdiction-id
     :moisture-percent                 (:moisture-target-percent lot)
     :germination-percent              (:germination-min-percent lot)
     :purity-percent                   (:purity-min-percent lot)
     :other-crop-seed-percent          (:other-crop-seed-max-percent lot)
     :seed-borne-disease-detected?     false
     :germinator-last-calibration-date (days-ago 10)
     :weight-variance-grams            20
     :seed-sources                     (vec seed-sources)
     :declared-traits                  (facts/seed-source-trait-set seed-sources)
     :sanitation-score                 85
     :evidence-checklist               (vec (:required-evidence j))}))

(def ^:private scenario-batches
  "Every batch this run puts in front of the Governor.

  `:defect` receives the at-spec record and that lot's own published window,
  and returns the record with exactly ONE measurement moved out of spec ---
  so each batch produces exactly one hard rule and the coverage table below
  maps one-to-one onto `seedops.governor`'s hard-check list.

  `batch-013` is deliberately absent from the store: `:coordinate-shipment`
  against a batch this facility never checked in must hard-hold on
  `:batch-not-registered`."
  [{:id "batch-001" :lot :maize/hybrid :jurisdiction :jp/maff
    :sources [:maize/hybrid-bt]
    :intent "at spec — drives the full approved lifecycle"}

   {:id "batch-002" :lot :wheat/certified :jurisdiction :us/usda
    :sources [:wheat/hard-red-certified]
    :intent "germination below the certification floor"
    :defect (fn [b lot]
              (assoc b :germination-percent
                     (- (:germination-min-percent lot) spec-margin)))}

   {:id "batch-003" :lot :soybean/certified :jurisdiction :eu/oecd
    :sources [:soybean/roundup-ready]
    :intent "GM herbicide-tolerance trait present but not declared"
    :defect (fn [b _lot] (assoc b :declared-traits #{}))}

   {:id "batch-004" :lot :vegetable/tomato :jurisdiction :jp/maff
    :sources [:tomato/hybrid-f1]
    :intent "seed-borne pathogen detected on this lot's own screening"
    :defect (fn [b _lot] (assoc b :seed-borne-disease-detected? true))}

   {:id "batch-005" :lot :maize/hybrid :jurisdiction :us/usda
    :sources [:maize/hybrid-bt]
    :intent "moisture above the safe storage window"
    :defect (fn [b lot]
              (assoc b :moisture-percent
                     (+ (:moisture-target-percent lot)
                        (* 2 (:moisture-tolerance-percent lot)))))}

   {:id "batch-006" :lot :wheat/certified :jurisdiction :jp/maff
    :sources [:wheat/soft-white-certified]
    :intent "germinator calibration past the 60-day limit"
    :defect (fn [b _lot] (assoc b :germinator-last-calibration-date (days-ago 90)))}

   {:id "batch-007" :lot :soybean/certified :jurisdiction :us/usda
    :sources [:soybean/conventional]
    :intent "germination test missing from the evidence checklist"
    :defect (fn [b _lot]
              (update b :evidence-checklist
                      (fn [ev] (vec (remove #{:germination-test} ev)))))}

   {:id "batch-008" :lot :vegetable/tomato :jurisdiction :eu/oecd
    :sources [:tomato/heirloom]
    :intent "quality concern raised and still open"
    :defect (fn [b _lot]
              (assoc b :quality-concern-raised? true :quality-concern-resolved? false))}

   {:id "batch-009" :lot :maize/hybrid :jurisdiction :eu/oecd
    :sources [:maize/hybrid-conventional]
    :intent "facility sanitation score below the 75 minimum"
    :defect (fn [b _lot] (assoc b :sanitation-score 60))}

   {:id "batch-010" :lot :wheat/certified :jurisdiction :eu/oecd
    :sources [:wheat/hard-red-certified]
    :intent "other-crop seed above this lot type's tolerance"
    :defect (fn [b lot]
              (assoc b :other-crop-seed-percent
                     (* 2 (:other-crop-seed-max-percent lot))))}

   {:id "batch-011" :lot :soybean/certified :jurisdiction :jp/maff
    :sources [:soybean/conventional]
    :intent "physical purity below the product window"
    :defect (fn [b lot]
              (assoc b :purity-percent (- (:purity-min-percent lot) spec-margin)))}

   {:id "batch-012" :lot :vegetable/tomato :jurisdiction :us/usda
    :sources [:tomato/hybrid-f1]
    :intent "finished-package weight variance over the 50 g tolerance"
    :defect (fn [b _lot] (assoc b :weight-variance-grams 60))}

   {:id "batch-013" :lot :maize/hybrid :jurisdiction :jp/maff
    :sources [:maize/hybrid-bt]
    :registered? false
    :intent "never checked in — shipment coordination must refuse it"}])

(defn- build-batch [{:keys [lot jurisdiction sources defect]}]
  (let [base (at-spec-batch lot jurisdiction sources)]
    (if defect (defect base (facts/seed-lot-type-by-id lot)) base)))

(defn- seed-store
  "The starting store: `{:batches {...} :facts []}`, seeded directly the way
  this repo's own tests seed it. `batch-013` is left out on purpose."
  []
  {:batches (into {}
                  (for [b scenario-batches
                        :when (not (false? (:registered? b)))]
                    [(:id b) (build-batch b)]))
   :facts   []})

(defn- batch-jurisdiction [b-id]
  (:jurisdiction (some #(when (= b-id (:id %)) %) scenario-batches)))

(defn- proposal
  "A well-formed advisor proposal. The citation is the jurisdiction's own
  published name out of `seedops.facts/jurisdictions` --- the Governor only
  checks that a citation exists, but citing anything this repo doesn't know
  about would defeat the point of the page."
  ([b-id] (proposal b-id {}))
  ([b-id overrides]
   (let [j (batch-jurisdiction b-id)]
     (merge {:cites      [{:spec (:name (facts/jurisdiction-by-id j))}]
             :value      {:jurisdiction j}
             :effect     :propose
             :confidence 0.9}
            overrides))))

;; ───────────────────────────── the run ──────────────────────────────

(def ^:private steps
  "The scenario, in execution order. Each entry is one proposal driven
  through `seedops.operation/run-operation`; `:approve?` marks the ones the
  facility operator signs off after the Governor escalates them."
  [{:label "登録 (承認あり)" :op :log-processing-batch :subject "batch-001" :approve? true}
   {:label "出荷確定 (承認あり)" :op :coordinate-shipment :subject "batch-001" :approve? true}
   {:label "設備保守 (自動確定)" :op :schedule-maintenance :subject "batch-001"}
   {:label "設備保守 (確信度不足)" :op :schedule-maintenance :subject "batch-001"
    :proposal {:confidence 0.4}}
   {:label "品質フラグ (承認あり)" :op :flag-quality-concern :subject "batch-008" :approve? true}

   {:label "二重登録" :op :log-processing-batch :subject "batch-001"}
   {:label "二重出荷確定" :op :coordinate-shipment :subject "batch-001"}
   {:label "発芽率不足" :op :log-processing-batch :subject "batch-002"}
   {:label "形質表示不一致" :op :log-processing-batch :subject "batch-003"}
   {:label "種子伝染性病害" :op :log-processing-batch :subject "batch-004"}
   {:label "水分逸脱" :op :log-processing-batch :subject "batch-005"}
   {:label "校正期限切れ" :op :log-processing-batch :subject "batch-006"}
   {:label "必要書類欠落" :op :log-processing-batch :subject "batch-007"}
   {:label "品質フラグ未解決" :op :log-processing-batch :subject "batch-008"}
   {:label "衛生スコア不足" :op :log-processing-batch :subject "batch-009"}
   {:label "他作物種子超過" :op :log-processing-batch :subject "batch-010"}
   {:label "純度不足" :op :log-processing-batch :subject "batch-011"}
   {:label "重量分散超過" :op :log-processing-batch :subject "batch-012"}
   {:label "未登録バッチの出荷" :op :coordinate-shipment :subject "batch-013"}
   {:label "許可外の操作" :op :operate-gravity-table :subject "batch-001"}
   {:label "propose 以外の effect" :op :schedule-maintenance :subject "batch-001"
    :proposal {:effect :commit}}
   {:label "法域引用なし" :op :flag-quality-concern :subject "batch-008"
    :proposal {:cites []}}])

(defn- disposition
  "How this run classifies a verdict. `:hard` is the un-overridable block;
  `:escalate` is the soft gate that reaches a human; `:auto-commit` is the
  only path the Governor lets through on its own."
  [verdict]
  (cond (:hard? verdict)    :hard
        (:escalate? verdict) :escalate
        (:ok? verdict)      :auto-commit
        :else               :unknown))

(defn- apply-commit
  "The store effect of an approved actuation. `seedops.store` exposes exactly
  two commit functions, and NEITHER takes an approver argument --- see the
  approver probe in `probe-rows`."
  [st op subject]
  (case op
    :log-processing-batch (store/log-batch st subject (store/production-batch st subject))
    :coordinate-shipment  (store/finalize-shipment st subject)
    st))

(defn- run-step
  "Drive one proposal through the real stack and fold the result into the run."
  [{:keys [store results] :as world} {:keys [label op subject approve?] :as spec}]
  (let [request  {:op op :subject subject}
        prop     (proposal subject (:proposal spec))
        result   (op/run-operation request context prop store governor/check)
        ;; run-operation only returns :verdict on the failure branch, so the
        ;; ok path is re-derived from the same pure Governor call it made.
        verdict  (or (:verdict result) (governor/check request context prop store))
        disp     (disposition verdict)
        approved? (and approve? (= :escalate disp))
        st1      (reduce store/append-fact store (:facts result))
        st2      (if approved?
                   (-> (apply-commit st1 op subject)
                       (store/append-fact
                        {:t :approval-committed :op op :actor actor-id :subject subject
                         :disposition :commit :by approver}))
                   st1)
        st3      (if (= :auto-commit disp)
                   (store/append-fact st2
                                      {:t :auto-commit :op op :actor actor-id :subject subject
                                       :disposition :commit :confidence (:confidence verdict)})
                   st2)]
    (assoc world
           :store st3
           :results (conj results
                          {:label label :op op :subject subject :verdict verdict
                           :disposition disp :approved? approved?
                           :approver (when approved? approver)
                           :actor-facts (vec (:facts result))
                           :ok? (:ok? result)}))))

(defn- run-demo!
  "Runs the whole scenario. Returns `{:store .. :results [..]}` --- every
  field the page renders is real output of this run."
  []
  (reduce run-step {:store (seed-store) :results []} steps))

;; ───────────────────────────── rendering ────────────────────────────

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw [v] (if (keyword? v) (subs (str v) 1) (str v)))

(defn- code [v] (str "<code>" (esc (kw v)) "</code>"))

(defn- kw-list
  "Keywords joined in explicit sort order --- sets and maps never reach the
  page in iteration order."
  [xs]
  (if (seq xs)
    (str/join ", " (map esc (sort (map kw xs))))
    "—"))

(defn- fmt-num [v]
  (cond (nil? v) "—"
        (and (number? v) (== v (Math/floor (double v)))) (str (long v))
        :else (str v)))

(defn- tag [class label] (str "<span class=\"" class "\">" label "</span>"))

(def ^:private hard-hold-marker
  "The exact string a HARD-hold cell renders. `-main` counts occurrences of
  this IN THE RENDERED DOCUMENT, so the build-time invariant is a claim about
  the page rather than about an in-memory value the page might not contain."
  "HARD hold · 上書き不可")

(defn- disposition-cell [{:keys [disposition approved?]}]
  (case disposition
    :hard        (tag "critical" hard-hold-marker)
    :escalate    (if approved?
                   (tag "ok" "escalate → 承認 → 確定")
                   (tag "warn" "escalate · 人間の署名待ち"))
    :auto-commit (tag "ok" "auto-commit")
    (tag "muted" "unknown")))

(defn- rows
  "Join already-rendered `<tr>` strings. Takes ONE collection on purpose --- a
  variadic version silently stringifies a passed-in lazy seq into
  `clojure.lang.LazySeq@...`, which is deterministic, byte-stable, and
  completely empty of data. That exact bug shipped in the first run of this
  generator and was caught by counting rendered rows, not by diffing bytes."
  [trs]
  (str/join "\n" trs))

(defn- tr [& cells]
  (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers body-rows]
  (str "    <div class=\"table-wrap\">\n"
       "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" % "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n"
       body-rows "\n"
       "      </tbody>\n"
       "    </table>\n"
       "    </div>\n"))

(defn- section [title lede body]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       "    <p class=\"muted\">" lede "</p>\n"
       body
       "  </section>\n"))

;; ── batches ──

(defn- lifecycle-cell [{:keys [processed? shipment-finalized?]}]
  (cond
    shipment-finalized? (tag "ok" "登録済 → 出荷確定")
    processed?          (tag "warn" "登録済 · 未出荷")
    :else               (tag "muted" "未登録")))

(defn- approver-cell
  "Who signed the commit --- and, crucially, whether the STORE kept it.
  `seedops.store/log-batch` and `finalize-shipment` take no approver
  argument, so this is derived, never asserted: the record actually written
  by the store is scanned for the approver id. If the store is later widened
  to keep it, this cell flips on its own. A dropped approver must not read
  the same as `nobody approved`."
  [record results b-id]
  (let [approved (filter #(and (= b-id (:subject %)) (:approver %)) results)]
    (if (empty? approved)
      (tag "muted" "承認なし")
      (let [who      (:approver (last approved))
            retained (some (fn [[_ v]] (= who v)) record)]
        (str (esc who) " "
             (if retained
               (tag "ok" "· store に保持")
               (tag "warn" "· store は保持せず (監査ファクトのみ)")))))))

(defn- batch-row [store results {:keys [id lot jurisdiction sources intent registered?]}]
  (let [record (store/production-batch store id)
        lot-m  (facts/seed-lot-type-by-id lot)
        j      (facts/jurisdiction-by-id jurisdiction)]
    (tr (code id)
        (str (esc (:name lot-m)) "<br><span class=\"muted\">" (code lot) "</span>")
        (esc (:name j))
        (kw-list sources)
        (if record
          (str (fmt-num (:moisture-percent record)) " / "
               (fmt-num (:germination-percent record)) " / "
               (fmt-num (:purity-percent record)) " / "
               (fmt-num (:other-crop-seed-percent record)))
          (tag "muted" "—"))
        (str (fmt-num (:moisture-target-percent lot-m)) "±" (fmt-num (:moisture-tolerance-percent lot-m))
             " / ≥" (fmt-num (:germination-min-percent lot-m))
             " / ≥" (fmt-num (:purity-min-percent lot-m))
             " / ≤" (fmt-num (:other-crop-seed-max-percent lot-m)))
        (if (false? registered?)
          (tag "critical" "store に未登録")
          (lifecycle-cell record))
        (approver-cell record results id)
        (esc intent))))

;; ── governor decisions ──

(defn- violation-cell [verdict]
  (let [vs (:violations verdict)]
    (if (seq vs)
      (str/join "<br>" (for [v (sort-by (comp kw :rule) vs)]
                         (str (code (:rule v)) " "
                              "<span class=\"muted\">" (esc (:detail v)) "</span>")))
      (tag "muted" "違反なし (soft gate のみ)"))))

(defn- decision-row [{:keys [label op subject verdict] :as r}]
  (tr (esc label)
      (code op)
      (code subject)
      (disposition-cell r)
      (fmt-num (:confidence verdict))
      (violation-cell verdict)))

;; ── hard-rule coverage ──

(def ^:private hard-rule-catalogue
  "Every hard rule `seedops.governor/check` can emit, in the order the
  namespace declares them. The page asserts coverage against this list ---
  a rule this run never triggered shows up as a gap rather than silently
  disappearing."
  [:op-not-allowed :effect-not-propose :no-spec-basis :evidence-incomplete
   :moisture-out-of-target :germination-rate-below-minimum :purity-below-minimum
   :other-crop-seed-exceeded :seed-borne-disease-detected
   :germinator-calibration-overdue :weight-variance-excessive
   :trait-label-mismatch :sanitation-score-insufficient :quality-flag-unresolved
   :already-processed :already-shipment-finalized :batch-not-registered])

(defn- coverage-row [results rule]
  (let [hits (for [r results
                   v (:violations (:verdict r))
                   :when (= rule (:rule v))]
               {:subject (:subject r) :detail (:detail v)})]
    (tr (code rule)
        (if (seq hits)
          (tag "critical" "HARD hold 発生")
          (tag "muted" "この run では未発火"))
        (if (seq hits) (code (:subject (first hits))) "—")
        (if (seq hits)
          (str "<span class=\"muted\">" (esc (:detail (first hits))) "</span>")
          "—"))))

;; ── ledger ──

(defn- ledger-row [{:keys [t op subject disposition basis by confidence]}]
  (tr (code t)
      (code (or op :n-a))
      (code subject)
      (code (or disposition :n-a))
      (if (seq basis) (kw-list basis) (tag "muted" "—"))
      (if by (esc by) (tag "muted" "—"))
      ;; `fmt-num`, not `clojure.core/num`: most ledger facts carry no
      ;; `:confidence`, and `num` renders that nil as an empty cell that reads
      ;; like a confidence of zero. An absent measurement must look absent.
      (fmt-num confidence)))

;; ── reference tables (straight out of seedops.facts) ──

(defn- lot-type-rows []
  (rows (for [[id m] (sort-by key facts/seed-lot-types)]
          (tr (code id) (esc (:name m))
              (str (fmt-num (:moisture-target-percent m)) " ± " (fmt-num (:moisture-tolerance-percent m)))
              (fmt-num (:germination-min-percent m))
              (fmt-num (:purity-min-percent m))
              (fmt-num (:other-crop-seed-max-percent m))))))

(defn- jurisdiction-rows []
  (rows (for [[id m] (sort-by key facts/jurisdictions)]
          (tr (code id) (esc (:name m))
              (if (:trait-declaration-required m) (tag "warn" "必要") (tag "muted" "不要"))
              (kw-list (:regulated-traits m))
              (kw-list (:required-evidence m))))))

(defn- seed-source-rows []
  (rows (for [[id m] (sort-by key facts/seed-source-trait-table)]
          (tr (code id)
              (if (:primary-trait m) (code (:primary-trait m)) (tag "muted" "—"))
              (if (seq (:cross-contact-risk m))
                (tag "warn" (kw-list (:cross-contact-risk m)))
                (tag "muted" "—"))))))

;; ── conformance probes (derived, never hard-coded) ──

(defn- probe-rows
  "Facts about the actor measured from THIS run's own output. Each row states
  what was probed, what came back, and what that implies --- so when the
  underlying behaviour changes, the row changes with it. Nothing here is a
  hand-written notice."
  [store results]
  (let [escalated   (first (filter #(= :escalate (:disposition %)) results))
        auto        (first (filter #(= :auto-commit (:disposition %)) results))
        e-fact      (first (:actor-facts escalated))
        hard-r      (first (filter #(= :hard (:disposition %)) results))
        h-fact      (first (:actor-facts hard-r))
        committed   (store/production-batch store "batch-001")
        commit-facts (filter #(= :approval-committed (:t %)) (store/audit-trail store))
        approver-in-record (some (fn [[_ v]] (= approver v)) committed)
        approver-in-actor-fact
        (some (fn [f] (some (fn [[_ v]] (= approver v)) f))
              (filter #(#{:governor-hold} (:t %)) (store/audit-trail store)))]
    (rows
     [(tr "承認パスで actor が返す監査ファクト"
          (code (str (count (:actor-facts auto)) " facts"))
          (if (seq (:actor-facts auto))
            (tag "ok" "actor が確定ファクトを書く")
            (tag "warn" "operation/run-operation は :ok? の枝で :facts [] を返す — 確定の記録は呼び出し側 (このコンソール) が書いている")))

      (tr "hold ファクトが hard と escalate を区別するか"
          (code (if (contains? (or e-fact {}) :hard?) "has :hard?" "no :hard? key"))
          (if (contains? (or e-fact {}) :hard?)
            (tag "ok" "actor がファクト上で区別している")
            (tag "warn" (str "governor/hold-fact は :hard? を記録しない — 台帳上、上書き不可の HARD hold と"
                             " 人間署名待ちの escalate はどちらも :disposition :hold で並ぶ。"
                             "この表の判定は run 時の verdict から導出している"))))

      (tr "escalate 時のファクトの :disposition"
          (code (:disposition e-fact))
          (if (= :hold (:disposition e-fact))
            (tag "warn" "人間の署名待ちも :hold として記録される (:basis が空かどうかが唯一の手掛かり)")
            (tag "ok" "escalate 専用の disposition を持つ")))

      (tr "HARD hold ファクトの :basis"
          (kw-list (:basis h-fact))
          (if (seq (:basis h-fact))
            (tag "ok" "違反 rule が台帳に残る")
            (tag "warn" "HARD hold なのに basis が空")))

      (tr "確定レコードに承認者が残るか"
          (code (if approver-in-record "retained" "dropped"))
          (if approver-in-record
            (tag "ok" "store が承認者を保持する")
            (tag "warn" (str "store/log-batch・finalize-shipment は承認者引数を持たない — 確定した batch 記録に "
                             (esc approver) " は残らない。承認者は "
                             (count commit-facts) " 件の :approval-committed 監査ファクトにのみ存在する"))))

      (tr "actor 自身のファクトに承認者が現れるか"
          (code (if approver-in-actor-fact "present" "absent"))
          (if approver-in-actor-fact
            (tag "ok" "actor のファクトが承認者を含む")
            (tag "warn" "actor が書くのは :governor-hold のみで、そこに承認者の欄は無い (:actor は actor id)")))])))

;; ── styling (DADS, taken from the copy this repo already vendors) ──

(def ^:private product-face
  "This repo's product page. It already vendors the DADS stylesheet inline, so
  it --- not a git coordinate --- is where the console gets its tokens."
  "docs/index.html")

(defn- dads-token-layer
  "The `:root { --color-… }` custom-property block out of `product-face`.

  Extracted rather than re-declared so the console and the product page cannot
  disagree about what `--color-key-900` is, and so the build stays offline.
  Throws instead of degrading: a console that silently loses its token layer
  still renders --- it just renders unstyled, with every `var()` falling back to
  nothing --- and that is exactly the failure this whole file is supposed to make
  impossible to ship unnoticed."
  []
  (let [f (io/file product-face)]
    (when-not (.exists f)
      (throw (ex-info (str "cannot style the console: " product-face " is missing, and it is "
                           "where the vendored DADS token layer comes from")
                      {:expected product-face})))
    (let [html  (slurp f)
          style (second (re-find #"(?s)<style>(.*?)</style>" html))
          root  (second (re-find #"(?s):root\s*\{(.*?)\}" (or style "")))]
      (when (str/blank? root)
        (throw (ex-info (str "cannot style the console: no `:root` custom-property block found in "
                             product-face " --- the product face no longer vendors DADS inline")
                        {:expected product-face})))
      (str ":root{" (str/trim root) "}"))))

(def ^:private console-css
  "Component rules for this console's small class vocabulary. Every colour, font
  and rule below resolves through a DADS primitive from `dads-token-layer` --- no
  raw hex, and nothing referenced that the token layer does not define (enforced
  in `-main`)."
  (str/join
   "\n"
   ["*,*::before,*::after{box-sizing:border-box}"
    (str "body{font-family:var(--font-family-sans);color:var(--color-neutral-solid-gray-800);"
         "background:var(--color-neutral-white);line-height:1.8;max-width:78rem;margin:0 auto;"
         "padding:2rem 1rem 4rem}")
    (str "h1{font-size:1.75rem;font-weight:700;line-height:1.4;margin:0;"
         "color:var(--color-neutral-solid-gray-900)}")
    (str "h2{font-size:1.375rem;font-weight:700;line-height:1.5;margin:0 0 .25rem;"
         "color:var(--color-neutral-solid-gray-900)}")
    "p{margin:0 0 1rem}"
    (str ".bar{display:flex;align-items:center;gap:.75rem;flex-wrap:wrap;padding:.75rem 0 1rem;"
         "margin-bottom:1.5rem;border-bottom:1px solid var(--color-neutral-solid-gray-200)}")
    (str ".badge{display:inline-block;font-size:.8125rem;font-weight:700;padding:.1rem .625rem;"
         "border-radius:1rem;background:var(--color-primitive-blue-50);color:var(--color-key-900);"
         "border:1px solid var(--color-primitive-blue-200)}")
    (str ".card{border:1px solid var(--color-neutral-solid-gray-200);border-radius:12px;"
         "padding:1.25rem 1.5rem;margin:1.25rem 0;background:var(--color-neutral-white)}")
    ".card>:first-child{margin-top:0}"
    ".card>:last-child{margin-bottom:0}"
    ".table-wrap{overflow-x:auto;max-width:100%}"
    "table{border-collapse:collapse;width:100%;margin:.75rem 0 .5rem;font-size:.8125rem}"
    (str "th,td{border:1px solid var(--color-neutral-solid-gray-300);padding:.5rem .75rem;"
         "text-align:left;vertical-align:top}")
    (str "th{background:var(--color-neutral-solid-gray-50);font-weight:700;"
         "color:var(--color-neutral-solid-gray-900);white-space:nowrap}")
    (str "code{font-family:var(--font-family-mono);background:var(--color-neutral-solid-gray-50);"
         "border:1px solid var(--color-neutral-solid-gray-200);border-radius:4px;padding:1px 5px;"
         "font-size:.9em}")
    ;; Governor dispositions. Weight carries the meaning as well as colour, so
    ;; the three states stay distinguishable without relying on hue alone.
    ".ok{color:var(--color-semantic-success-2);font-weight:700}"
    ".warn{color:var(--color-semantic-warning-yellow-2);font-weight:700}"
    ".critical{color:var(--color-semantic-error-1);font-weight:700}"
    ".muted{color:var(--color-neutral-solid-gray-600);font-weight:400}"
    (str "footer{margin-top:3rem;padding-top:1.5rem;"
         "border-top:1px solid var(--color-neutral-solid-gray-200);"
         "color:var(--color-neutral-solid-gray-600);font-size:.875rem}")]))

;; ── document ──

(defn- render
  [{:keys [store results]}]
  (let [ledger (store/audit-trail store)
        hard-results (filter #(= :hard (:disposition %)) results)]
    (str
     "<!DOCTYPE html>\n"
     "<html lang=\"ja\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
     "<title>cloud-itonami-isic-0164 · 種子調製 (propagation) オペレーターコンソール</title>"
     "<style>" (dads-token-layer) "\n" console-css "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Seed processing for propagation (ISIC 0164) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · バッチ登録と出荷確定は常に人間の署名が要る</span>\n"
     "</header>\n"
     "<main>\n"

     (section
      "処理バッチ"
      (str "この run が Governor に掛けた全バッチ。測定値・規格窓とも "
           "<code>seedops.facts/seed-lot-types</code> 由来で、規格内の値は各 seed-lot type の"
           "公表窓そのもの、規格外の値はその窓から一意の変換で導いている。"
           "ページは <code>clojure -M:dev:render-html</code> が実 actor 実行から生成する。")
      (table ["Batch" "Seed-lot type" "法域" "Seed source" "測定値 (水分/発芽/純度/他作物 %)"
              "規格窓" "ライフサイクル" "承認者" "この run での役割"]
             (rows (map (partial batch-row store results) scenario-batches))))

     (section
      "Governor の判定 (この run)"
      (str "各行は <code>seedops.operation/run-operation</code> を 1 回通した実結果。"
           "HARD hold は上書き不可でそのまま止まる。escalate は Governor が clean と判断しても"
           "人間の署名を要求する soft gate で、<code>:log-processing-batch</code>・"
           "<code>:coordinate-shipment</code>・<code>:flag-quality-concern</code>・確信度不足がこれに当たる。"
           "違反の説明文は Governor 自身の出力をそのまま出している。")
      (table ["シナリオ" "Op" "Batch" "判定" "確信度" "違反 rule / Governor の説明"]
             (rows (map decision-row results))))

     (section
      "HARD rule カバレッジ"
      (str "<code>seedops.governor</code> が出しうる hard rule の全一覧に対して、"
           "この run で実際に発火したものを突き合わせたもの。発火しなかった rule は"
           "消えるのではなく未発火として残る。")
      (table ["Rule" "この run" "発火したバッチ" "Governor の説明 (実出力)"]
             (rows (map (partial coverage-row results) hard-rule-catalogue))))

     (section
      "Actor 適合プローブ"
      (str "actor の実際の挙動をこの run の出力から測ったもの。判定はすべて実行時に導出しており、"
           "固定文言ではない — 実装が変われば行の内容も一緒に変わる。")
      (table ["プローブ" "測定結果" "含意"]
             (probe-rows store results)))

     (section
      "監査台帳 (この run)"
      (str "<code>seedops.store/audit-trail</code> の append-only 台帳。"
           "<code>:governor-hold</code> は actor が書いたもの、"
           "<code>:approval-committed</code> / <code>:auto-commit</code> は"
           "actor が確定側のファクトを返さないため呼び出し側が書いたもの (上のプローブ参照)。")
      (table ["Fact" "Op" "Batch" "Disposition" "Basis" "承認者" "確信度"]
             (rows (map ledger-row ledger))))

     (section
      "参照: seed-lot type 規格窓"
      "<code>seedops.facts/seed-lot-types</code> の全件。上の測定値はここから導出している。"
      (table ["ID" "名称" "水分目標 ± 許容 (%)" "最低発芽率 (%)" "最低純度 (%)" "他作物種子上限 (%)"]
             (lot-type-rows)))

     (section
      "参照: 法域要件"
      "<code>seedops.facts/jurisdictions</code> の全件。evidence checklist はここから複写している。"
      (table ["ID" "名称" "形質申告" "規制対象形質" "必要書類"]
             (jurisdiction-rows)))

     (section
      "参照: seed source の形質"
      (str "<code>seedops.facts/seed-source-trait-table</code> の全件。"
           "cross-contact は隣接 GM 圃場からの交雑による adventitious presence リスクで、"
           "Governor の形質表示チェックは primary trait のみを見る。")
      (table ["Seed source" "Primary trait" "Cross-contact risk"]
             (seed-source-rows)))

     "</main>\n"
     "<footer>\n"
     "  <p>生成: <code>clojure -M:dev:render-html</code> (<code>seedops.render-html</code>) — "
     "実 actor 実行の出力。決定論的 (タイムスタンプ・乱数・map 順序に依存しない)。<br>\n"
     "  この run: HARD hold " (count hard-results) " 件 / 判定 " (count results) " 件 / 監査ファクト "
     (count ledger) " 件 / バッチ " (count scenario-batches) " 件。</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn- occurrences
  "How many times `sub` appears in `s`."
  [s sub]
  (loop [i 0 n 0]
    (let [j (str/index-of s sub i)]
      (if j (recur (+ j (count sub)) (inc n)) n))))

(defn- unresolved-css-vars
  "Every `var(--x)` the page references but never defines. A page whose token
  layer failed to load still renders --- it just renders unstyled --- so this is
  checked as a build invariant rather than left to whoever opens the file."
  [html]
  (let [used    (set (map second (re-seq #"var\((--[a-z0-9-]+)" html)))
        defined (set (map second (re-seq #"(--[a-z0-9-]+)\s*:" html)))]
    (sort (remove defined used))))

(defn -main [& args]
  (let [out       (or (first args) "docs/samples/operator-console.html")
        world     (run-demo!)
        html      (render world)
        ledger    (store/audit-trail (:store world))
        ;; Counted out of the RENDERED DOCUMENT, not out of the run: an
        ;; earlier revision of this generator counted the in-memory results
        ;; and happily reported 17 HARD holds while every table body in the
        ;; page held a stringified lazy seq and not one row of data.
        hard-n    (occurrences html hard-hold-marker)
        row-n     (occurrences html "        <tr>")
        section-n (occurrences html "<section class=\"card\">")]
    (when (zero? hard-n)
      (throw (ex-info (str "operator console rendered 0 HARD governor holds — the page must "
                           "demonstrate at least one un-overridable governor block")
                      {:hard-holds 0 :decisions (count (:results world))})))
    (when (< row-n (count (:results world)))
      (throw (ex-info (str "operator console rendered fewer data rows (" row-n ") than the run "
                           "produced decisions (" (count (:results world)) ") — a table body "
                           "was dropped or stringified instead of rendered")
                      {:rendered-rows row-n :decisions (count (:results world))})))
    (when-let [dangling (seq (unresolved-css-vars html))]
      (throw (ex-info (str "operator console references " (count dangling) " CSS custom "
                           "propert" (if (= 1 (count dangling)) "y" "ies")
                           " nothing defines — the DADS token layer did not survive into the "
                           "page, which would ship a silently unstyled console")
                      {:unresolved (vec dangling)})))
    (spit out html)
    (println "wrote" out
             (str "(" hard-n " HARD holds, " section-n " sections, " row-n " data rows, "
                  (count (:results world)) " decisions, "
                  (count ledger) " ledger facts, "
                  (count scenario-batches) " batches)"))))
