(ns seedops.store
  "Store abstraction for seed-processing-for-propagation batches. Current
  implementation operates on plain data (`{:batches {batch-id batch-map}
  :facts [...]}`); production should migrate this seam to Datomic/
  kotoba-server (the same seam point all cloud-itonami actors use) while
  keeping the same pure-function surface.

  A processing batch is the minimal unit of work: one cleaning/grading/
  testing run of a seed lot, tracked from intake through cleaning,
  grading, germination/purity/disease testing, and shipment. Representative
  batch keys:
    - :seed-lot-type keyword seed-lot id (see `seedops.facts/seed-lot-types`)
    - :jurisdiction keyword jurisdiction id (see `seedops.facts/jurisdictions`)
    - :moisture-percent / :germination-percent / :purity-percent /
      :other-crop-seed-percent finished-lot actuals
    - :seed-borne-disease-detected? true if laboratory pathogen screening
      flagged fungal, bacterial, or viral seed-borne disease
    - :sanitation-score 0-100 facility hygiene/cross-contamination-control score
    - :germinator-last-calibration-date epoch-ms of last germination-testing
      equipment (incubator/germinator/seed-counter) calibration
    - :weight-variance-grams finished-package weight drift from target
    - :seed-sources variety/cultivar ids processed/blended for this batch
    - :declared-traits set of declared regulated-trait keywords
    - :evidence-checklist evidence items present for the batch
    - :quality-concern-raised? / :quality-concern-resolved? open quality flag
    - :processed? true once a `:log-processing-batch` proposal commits
    - :shipment-finalized? true once a `:coordinate-shipment` proposal commits

  The ledger (`:facts`) is a separate append-only vector of audit facts,
  kept alongside `:batches` in the same store value.")

(defn production-batch
  "Retrieve a batch by id, or nil if it does not exist / is not yet
  registered."
  [st batch-id]
  (get-in st [:batches batch-id]))

(defn batch-already-processed?
  "True only if the batch exists and has already been marked processed."
  [st batch-id]
  (true? (:processed? (production-batch st batch-id))))

(defn batch-shipment-finalized?
  "True only if the batch exists and its shipment has already been
  finalized."
  [st batch-id]
  (true? (:shipment-finalized? (production-batch st batch-id))))

(defn log-batch
  "Register/update `batch-data` under `batch-id` and mark it processed
  (one-way flag). Used once a `:log-processing-batch` proposal commits."
  [st batch-id batch-data]
  (assoc-in st [:batches batch-id] (assoc batch-data :processed? true)))

(defn finalize-shipment
  "Mark an existing batch's shipment as finalized (one-way flag). Used once
  a `:coordinate-shipment` proposal commits."
  [st batch-id]
  (assoc-in st [:batches batch-id :shipment-finalized?] true))

(defn audit-trail
  "Return the append-only audit ledger (empty vector if none yet)."
  [st]
  (get st :facts []))

(defn append-fact
  "Append `fact` to the store's audit ledger."
  [st fact]
  (update st :facts (fnil conj []) fact))
