(ns seedops.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [seedops.facts :as facts]))

;; ──────────────────────── Seed-Lot Type Lookups ──────────────────────

(deftest seed-lot-type-by-id-test
  (testing "hybrid maize seed-lot type exists"
    (let [p (facts/seed-lot-type-by-id :maize/hybrid)]
      (is (some? p))
      (is (= (:id p) :maize/hybrid))
      (is (= (:moisture-target-percent p) 13.0))
      (is (= (:germination-min-percent p) 90.0))))

  (testing "tomato seed-lot type exists"
    (let [p (facts/seed-lot-type-by-id :vegetable/tomato)]
      (is (some? p))
      (is (= (:purity-min-percent p) 97.0))
      (is (= (:germination-min-percent p) 75.0))))

  (testing "nonexistent seed-lot type returns nil"
    (is (nil? (facts/seed-lot-type-by-id :maize/nonexistent)))))

;; ──────────────────────── Jurisdiction Lookups ──────────────────────

(deftest jurisdiction-by-id-test
  (testing "JP MAFF jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :jp/maff)]
      (is (some? j))
      (is (true? (:trait-declaration-required j)))
      (is (contains? (:regulated-traits j) :gm-bt-trait))))

  (testing "US USDA jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :us/usda)]
      (is (some? j))
      (is (contains? (:regulated-traits j) :gm-ht-trait))))

  (testing "EU OECD jurisdiction includes hybrid-f1-trait as a regulated trait"
    (let [j (facts/jurisdiction-by-id :eu/oecd)]
      (is (some? j))
      (is (contains? (:regulated-traits j) :hybrid-f1-trait))))

  (testing "nonexistent jurisdiction returns nil"
    (is (nil? (facts/jurisdiction-by-id :xx/unknown)))))

;; ──────────────────────── Trait Lookups ──────────────────────

(deftest seed-source-traits-test
  (testing "Bt hybrid maize has gm-bt-trait"
    (let [a (facts/seed-source-traits :maize/hybrid-bt)]
      (is (= (:primary-trait a) :gm-bt-trait))))

  (testing "Roundup Ready soybean has gm-ht-trait"
    (let [a (facts/seed-source-traits :soybean/roundup-ready)]
      (is (= (:primary-trait a) :gm-ht-trait))))

  (testing "conventional maize has no primary trait but carries gm-bt-trait cross-contact risk"
    (let [a (facts/seed-source-traits :maize/hybrid-conventional)]
      (is (nil? (:primary-trait a)))
      (is (contains? (:cross-contact-risk a) :gm-bt-trait))))

  (testing "nonexistent seed source returns nil"
    (is (nil? (facts/seed-source-traits :unknown/variety)))))

;; ──────────────────────── Seed-Processing Safety Predicates ──────────

(deftest moisture-in-range-test
  (testing "moisture within tolerance passes"
    (let [p (facts/seed-lot-type-by-id :maize/hybrid)]
      (is (true? (facts/moisture-in-range? 13.0 p)))))

  (testing "moisture at lower tolerance boundary passes"
    (let [p (facts/seed-lot-type-by-id :maize/hybrid)]
      (is (true? (facts/moisture-in-range? 12.5 p)))))

  (testing "moisture below range fails"
    (let [p (facts/seed-lot-type-by-id :maize/hybrid)]
      (is (false? (facts/moisture-in-range? 12.0 p)))))

  (testing "moisture above range fails"
    (let [p (facts/seed-lot-type-by-id :maize/hybrid)]
      (is (false? (facts/moisture-in-range? 14.0 p))))))

(deftest germination-in-range-test
  (testing "germination at or above minimum passes"
    (let [p (facts/seed-lot-type-by-id :maize/hybrid)]
      (is (true? (facts/germination-in-range? 90.0 p)))
      (is (true? (facts/germination-in-range? 95.0 p)))))

  (testing "germination below minimum fails"
    (let [p (facts/seed-lot-type-by-id :maize/hybrid)]
      (is (false? (facts/germination-in-range? 85.0 p))))))

(deftest purity-in-range-test
  (testing "purity at or above minimum passes"
    (let [p (facts/seed-lot-type-by-id :wheat/certified)]
      (is (true? (facts/purity-in-range? 99.0 p)))))

  (testing "purity below minimum fails"
    (let [p (facts/seed-lot-type-by-id :wheat/certified)]
      (is (false? (facts/purity-in-range? 95.0 p))))))

(deftest other-crop-seed-in-range-test
  (testing "other-crop-seed at or below maximum passes"
    (let [p (facts/seed-lot-type-by-id :wheat/certified)]
      (is (true? (facts/other-crop-seed-in-range? 0.3 p)))
      (is (true? (facts/other-crop-seed-in-range? 0.1 p)))))

  (testing "other-crop-seed above maximum fails"
    (let [p (facts/seed-lot-type-by-id :wheat/certified)]
      (is (false? (facts/other-crop-seed-in-range? 0.5 p))))))

;; ──────────────────────── Trait Traceability ──────────────────────

(deftest seed-source-trait-set-test
  (testing "Bt-only formulation collects gm-bt-trait"
    (let [seed-sources [:maize/hybrid-bt]
          traits (facts/seed-source-trait-set seed-sources)]
      (is (contains? traits :gm-bt-trait))))

  (testing "blended formulation includes multiple traits"
    (let [seed-sources [:maize/hybrid-bt :soybean/roundup-ready :tomato/hybrid-f1]
          traits (facts/seed-source-trait-set seed-sources)]
      (is (contains? traits :gm-bt-trait))
      (is (contains? traits :gm-ht-trait))
      (is (contains? traits :hybrid-f1-trait))))

  (testing "trait-free seed sources produce empty set"
    (let [seed-sources [:wheat/hard-red-certified :tomato/heirloom]
          traits (facts/seed-source-trait-set seed-sources)]
      (is (empty? traits))))

  (testing "conventional maize alone contributes no primary trait (only cross-contact risk, informational)"
    (let [seed-sources [:maize/hybrid-conventional]
          traits (facts/seed-source-trait-set seed-sources)]
      (is (empty? traits)))))

(deftest trait-declaration-complete-test
  (testing "declaration matches formulation for jurisdiction"
    (let [j (facts/jurisdiction-by-id :jp/maff)
          seed-sources [:maize/hybrid-bt]
          declared #{:gm-bt-trait}]
      (is (true? (facts/trait-declaration-complete? j seed-sources declared)))))

  (testing "incomplete declaration fails"
    (let [j (facts/jurisdiction-by-id :jp/maff)
          seed-sources [:maize/hybrid-bt :soybean/roundup-ready]
          declared #{:gm-bt-trait}]
      (is (false? (facts/trait-declaration-complete? j seed-sources declared)))))

  (testing "extra declarations pass (conservative)"
    (let [j (facts/jurisdiction-by-id :jp/maff)
          seed-sources [:maize/hybrid-bt]
          declared #{:gm-bt-trait :gm-ht-trait}]
      (is (true? (facts/trait-declaration-complete? j seed-sources declared))))))

;; ──────────────────────── Evidence Completeness ──────────────────────

(deftest required-evidence-satisfied-test
  (testing "complete evidence checklist passes"
    (let [j (facts/jurisdiction-by-id :jp/maff)
          evidence [:seed-lot-intake-record :cleaning-grading-log :germination-test
                    :purity-test :moisture-test :seed-borne-disease-test :trait-declaration :weight-check]]
      (is (true? (facts/required-evidence-satisfied? j evidence)))))

  (testing "incomplete evidence fails"
    (let [j (facts/jurisdiction-by-id :jp/maff)
          evidence [:seed-lot-intake-record :cleaning-grading-log]]
      (is (false? (facts/required-evidence-satisfied? j evidence))))))
