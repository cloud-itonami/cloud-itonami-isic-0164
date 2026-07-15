(ns seedops.facts
  "Reference facts for seed-processing-for-propagation facilities: seed-lot
  type quality windows (storage moisture / minimum germination rate /
  minimum physical purity / maximum other-crop-seed contamination),
  jurisdiction trait-declaration and evidence-checklist requirements, and
  per-seed-source (variety/cultivar) trait data. This namespace contains
  pure lookup functions for regulatory/seed-quality compliance checks --
  the Governor calls these to independently validate proposals; the
  advisor's confidence is never sufficient on its own.

  A seed-processing-for-propagation facility cleans, grades, tests and
  certifies seed lots destined to be PLANTED (grown into a future crop),
  not consumed -- this is what distinguishes ISIC 0164 from grain-mill
  post-harvest processing (ISIC 1061), whose finished product is consumed
  or further milled for food. The quality bar is therefore VIABILITY
  (germination rate) and VARIETAL/PHYSICAL PURITY, not food-safety
  contamination levels, though seed-borne disease and moisture-driven
  spoilage remain genuine hazards -- to the future crop, not to a
  consumer."
  (:require [clojure.set :as set]))

(def seed-lot-types
  "Valid seed-lot categories destined for propagation and their safe
  processing/storage windows. `germination-min-percent` is the minimum
  fraction of seeds in the lot expected to produce a normal seedling
  under standard test conditions (ISTA/AOSA methodology) -- this is the
  single most important seed-processing-for-propagation quality
  indicator, since a seed lot below this floor cannot be certified for
  planting regardless of how clean or well-graded it otherwise is.
  `purity-min-percent` is the minimum physical/analytic purity (pure
  seed fraction by weight, excluding inert matter, other-crop seed, and
  weed seed). `other-crop-seed-max-percent` is the maximum tolerated
  fraction of seed from a DIFFERENT crop species/variety mixed into the
  lot -- deliberately per-seed-lot-type since hybrid seed for
  commercial planting carries a much tighter varietal-purity tolerance
  than open-pollinated vegetable seed carries."
  {:maize/hybrid
   {:id :maize/hybrid
    :name "ハイブリッドとうもろこし種子"
    :moisture-target-percent 13.0
    :moisture-tolerance-percent 0.5
    :germination-min-percent 90.0
    :purity-min-percent 98.0
    :other-crop-seed-max-percent 0.5}

   :wheat/certified
   {:id :wheat/certified
    :name "認証小麦種子"
    :moisture-target-percent 12.0
    :moisture-tolerance-percent 0.5
    :germination-min-percent 85.0
    :purity-min-percent 99.0
    :other-crop-seed-max-percent 0.3}

   :soybean/certified
   {:id :soybean/certified
    :name "認証大豆種子"
    :moisture-target-percent 11.0
    :moisture-tolerance-percent 0.5
    :germination-min-percent 80.0
    :purity-min-percent 98.0
    :other-crop-seed-max-percent 0.5}

   :vegetable/tomato
   {:id :vegetable/tomato
    :name "トマト種子"
    :moisture-target-percent 7.0
    :moisture-tolerance-percent 0.5
    :germination-min-percent 75.0
    :purity-min-percent 97.0
    :other-crop-seed-max-percent 1.0}})

(defn seed-lot-type-by-id [id]
  (get seed-lot-types id))

(def jurisdictions
  "Seed-processing-for-propagation jurisdictions and their trait-
  declaration and evidence-checklist requirements."
  {:jp/maff
   {:id :jp/maff
    :name "日本 (種苗法・農林水産省)"
    :trait-declaration-required true
    :regulated-traits #{:gm-bt-trait :gm-ht-trait}
    :required-evidence
    [:seed-lot-intake-record
     :cleaning-grading-log
     :germination-test
     :purity-test
     :moisture-test
     :seed-borne-disease-test
     :trait-declaration
     :weight-check]}

   :us/usda
   {:id :us/usda
    :name "United States (Federal Seed Act / USDA-AMS)"
    :trait-declaration-required true
    :regulated-traits #{:gm-bt-trait :gm-ht-trait}
    :required-evidence
    [:seed-lot-intake-record
     :cleaning-grading-log
     :germination-test
     :purity-test
     :moisture-test
     :seed-borne-disease-test
     :trait-declaration
     :weight-check]}

   :eu/oecd
   {:id :eu/oecd
    :name "European Union (OECD Seed Schemes / EU Seed Marketing Directive)"
    :trait-declaration-required true
    :regulated-traits #{:gm-bt-trait :gm-ht-trait :hybrid-f1-trait}
    :required-evidence
    [:seed-lot-intake-record
     :cleaning-grading-log
     :germination-test
     :purity-test
     :moisture-test
     :seed-borne-disease-test
     :trait-declaration
     :weight-check]}})

(defn jurisdiction-by-id [id]
  (get jurisdictions id))

(def seed-source-trait-table
  "Per-seed-source (variety/cultivar) primary regulated trait and
  cross-contact (adventitious presence) risk, used to derive a
  processing batch's trait set for label-accuracy verification.
  `:maize/hybrid-conventional` and `:soybean/conventional` carry no
  primary trait of their own but carry a real-world adventitious-
  presence risk from cross-pollination with a GM neighbor field --
  exactly why a conventional/non-GM label claim requires verified
  isolation distance or identity-preservation controls. Seed sources
  with no trait relevance map to nil."
  {:maize/hybrid-bt            {:primary-trait :gm-bt-trait :cross-contact-risk #{}}
   :maize/hybrid-conventional  {:primary-trait nil :cross-contact-risk #{:gm-bt-trait}}
   :wheat/hard-red-certified   {:primary-trait nil :cross-contact-risk #{}}
   :wheat/soft-white-certified {:primary-trait nil :cross-contact-risk #{}}
   :soybean/roundup-ready      {:primary-trait :gm-ht-trait :cross-contact-risk #{}}
   :soybean/conventional       {:primary-trait nil :cross-contact-risk #{:gm-ht-trait}}
   :tomato/heirloom            {:primary-trait nil :cross-contact-risk #{}}
   :tomato/hybrid-f1           {:primary-trait :hybrid-f1-trait :cross-contact-risk #{}}})

(defn seed-source-traits [id]
  (get seed-source-trait-table id))

(defn seed-source-trait-set
  "Given a processing batch's seed-source-id list, return the set of
  primary regulated traits actually present. Non-trait-bearing /
  unknown seed-source ids contribute nothing."
  [seed-sources]
  (into #{}
        (keep (fn [id] (:primary-trait (seed-source-traits id))))
        seed-sources))

(defn trait-declaration-complete?
  "Verify that `declared` traits are a superset of the batch's actual
  traits for `seed-sources`. Extra (conservative) declarations pass;
  omissions fail. `jurisdiction` is accepted for call-site symmetry with
  other facts lookups."
  [_jurisdiction seed-sources declared]
  (set/subset? (seed-source-trait-set seed-sources) (set declared)))

(defn required-evidence-satisfied?
  "Verify that every item in the jurisdiction's `:required-evidence` list
  is present in `evidence`. `jurisdiction` may be a resolved jurisdiction
  map (as returned by `jurisdiction-by-id`) or a raw jurisdiction id --
  both call conventions are in use (tests pass a resolved map; the
  Governor passes the raw id straight off batch metadata)."
  [jurisdiction evidence]
  (let [j (if (map? jurisdiction) jurisdiction (jurisdiction-by-id jurisdiction))]
    (if-not j
      false
      (set/subset? (set (:required-evidence j)) (set evidence)))))

(defn moisture-in-range?
  "Positive-sense convenience predicate: does `percent` fall within
  `seed-lot`'s moisture tolerance window (inclusive) around its target?
  Propagation seed lots outside their moisture window risk mold/loss of
  viability in storage (too high) or seed-coat embrittlement/damage
  during cleaning and grading (too low)."
  [percent seed-lot]
  (boolean
   (and (some? seed-lot)
        (let [target (:moisture-target-percent seed-lot)
              tol (:moisture-tolerance-percent seed-lot)]
          (and (>= percent (- target tol))
               (<= percent (+ target tol)))))))

(defn germination-in-range?
  "Positive-sense convenience predicate: is `percent` at or above
  `seed-lot`'s minimum required germination rate?"
  [percent seed-lot]
  (boolean
   (and (some? seed-lot)
        (>= percent (:germination-min-percent seed-lot)))))

(defn purity-in-range?
  "Positive-sense convenience predicate: is `percent` at or above
  `seed-lot`'s minimum required physical/analytic purity?"
  [percent seed-lot]
  (boolean
   (and (some? seed-lot)
        (>= percent (:purity-min-percent seed-lot)))))

(defn other-crop-seed-in-range?
  "Positive-sense convenience predicate: does `percent` stay at or below
  `seed-lot`'s maximum tolerated other-crop-seed contamination?"
  [percent seed-lot]
  (boolean
   (and (some? seed-lot)
        (<= percent (:other-crop-seed-max-percent seed-lot)))))
