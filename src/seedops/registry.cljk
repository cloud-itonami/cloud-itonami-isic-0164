(ns seedops.registry
  "Pure validation functions for seed-processing-for-propagation
  parameters. These are called by the Governor to independently verify
  physical/operational constraints -- the advisor's confidence is NOT
  sufficient to override these checks.

  All functions here are pure arithmetic/set/boolean predicates with no
  host-clock or I/O calls, so this namespace stays trivially portable
  across Clojure/ClojureScript. Callers that need the current time (see
  `germinator-calibration-overdue?`) obtain it themselves via a
  `:clj`/`:cljs` reader-conditional at the call site (see
  `seedops.governor`)."
  (:require [clojure.set :as set]))

(defn moisture-out-of-target?
  "Independently verify that the batch's finished seed-lot moisture falls
  within tolerance of the seed-lot type's target moisture. Propagation
  seed lots outside their moisture window risk mold growth and loss of
  viability in storage (too high) or seed-coat embrittlement/mechanical
  damage during cleaning and grading (too low)."
  [actual-percent target-percent tolerance-percent]
  (or (< actual-percent (- target-percent tolerance-percent))
      (> actual-percent (+ target-percent tolerance-percent))))

(defn germination-rate-below-minimum?
  "Independently verify that the batch's actual germination rate (%) does
  not fall below the seed-lot type's minimum required rate. Germination
  rate is the single most serious viability hazard specific to seed
  processing for propagation -- a lot below the regulatory/certification
  floor cannot be sold or planted as propagation seed regardless of how
  clean or well-graded it otherwise is, a hard, un-overridable stop."
  [actual-percent min-percent]
  (< actual-percent min-percent))

(defn purity-below-minimum?
  "Independently verify that the batch's physical/analytic purity (pure
  seed fraction by weight, a core seed-processing quality indicator)
  meets the seed-lot type's minimum required purity. Below-minimum
  purity indicates excessive inert matter, other-crop seed, or weed seed
  content -- a varietal-integrity and grade-misclassification hazard."
  [actual-percent min-percent]
  (< actual-percent min-percent))

(defn other-crop-seed-exceeded?
  "Independently verify that the batch's other-crop-seed contamination
  (%) does not exceed the seed-lot type's maximum tolerance. Contamination
  above range indicates a cleaning/grading-line fault and risks
  misidentifying the wrong species/variety being planted downstream."
  [actual-percent max-percent]
  (> actual-percent max-percent))

(defn germinator-calibration-overdue?
  "Independently verify that the germination-testing equipment
  (incubator/germinator, seed counter -- the instruments that produce
  the batch's own germination-rate result) was calibrated within the
  last 60 days. `last-calibration-epoch-ms` and `now-epoch-ms` are both
  epoch milliseconds -- callers obtain `now` via a `:clj`/`:cljs`
  reader-conditional, keeping this namespace free of any host-clock
  call. A shorter interval than a grain-mill actor's metal-detection
  calibration (90 days) reflects the sensitivity of biological
  germination testing to environmental drift -- an out-of-calibration
  incubator silently invalidates the very test this actor's Governor
  relies on."
  [last-calibration-epoch-ms now-epoch-ms]
  (> (- now-epoch-ms last-calibration-epoch-ms)
     (* 60 24 60 60 1000)))

(defn weight-variance-excessive?
  "Independently verify that a batch's finished-package weight variance
  (drift from target, in grams) does not exceed the maximum tolerance.
  Excessive variance indicates the packaging scale is out of calibration
  or the processing yield was measured incorrectly."
  [actual-variance-grams max-variance-grams]
  (> actual-variance-grams max-variance-grams))

(defn trait-label-risk?
  "True when the batch's seed-source formulation contains a regulated
  trait NOT present in the declared-traits set (mislabeling /
  under-declaration risk -- a genuine varietal-integrity and legal-
  compliance hazard for growers relying on the label, and especially
  for conventional/non-GM-labeled lots grown near a GM neighbor field).
  Declaring MORE traits than the batch actually contains is conservative
  and never a risk."
  [formula-traits declared-traits]
  (not (set/subset? (set formula-traits) (set declared-traits))))

(defn seed-borne-disease-detected?
  "Independently verify a batch's seed-borne-disease-detection result
  (pathogen screening -- fungal, bacterial, or viral seed-borne disease
  caught by laboratory testing). Any detection is a genuine hazard to
  the FUTURE crop grown from this seed -- this predicate simply coerces
  the raw fact to a boolean so the Governor's check functions stay
  uniform in shape with every other independently-verified physical
  constraint in this namespace."
  [actual-detected?]
  (boolean actual-detected?))

(defn sanitation-score-insufficient?
  "Independently verify that the facility's pre-processing sanitation/
  cross-contamination-control score meets the minimum required. Score is
  0-100, assessed by a third-party auditor against seed-processing
  sanitation standards (a significant concern specific to preventing
  lot-to-lot varietal cross-contamination and pest infestation in bulk
  seed storage and processing)."
  [actual-score min-score-required]
  (< actual-score min-score-required))
