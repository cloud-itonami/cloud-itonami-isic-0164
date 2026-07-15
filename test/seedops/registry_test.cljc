(ns seedops.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [seedops.registry :as registry]))

;; ──────────────────────── Moisture Target ──────────────────────

(deftest moisture-out-of-target-test
  (testing "moisture at target with no tolerance returns false"
    (is (false? (registry/moisture-out-of-target? 13.0 13.0 0.5))))

  (testing "moisture within tolerance range returns false"
    (is (false? (registry/moisture-out-of-target? 12.7 13.0 0.5))))

  (testing "moisture below tolerance returns true (violation)"
    (is (true? (registry/moisture-out-of-target? 12.0 13.0 0.5))))

  (testing "moisture above tolerance returns true (violation)"
    (is (true? (registry/moisture-out-of-target? 13.6 13.0 0.5)))))

;; ──────────────────────── Germination Rate ──────────────────────

(deftest germination-rate-below-minimum-test
  (testing "rate above minimum returns false (no violation)"
    (is (false? (registry/germination-rate-below-minimum? 95 90))))

  (testing "rate at minimum returns false"
    (is (false? (registry/germination-rate-below-minimum? 90 90))))

  (testing "rate below minimum returns true (violation)"
    (is (true? (registry/germination-rate-below-minimum? 85 90)))))

;; ──────────────────────── Purity ──────────────────────

(deftest purity-below-minimum-test
  (testing "purity above minimum returns false (no violation)"
    (is (false? (registry/purity-below-minimum? 99.0 98.0))))

  (testing "purity at minimum returns false"
    (is (false? (registry/purity-below-minimum? 98.0 98.0))))

  (testing "purity below minimum returns true (violation)"
    (is (true? (registry/purity-below-minimum? 95.0 98.0)))))

;; ──────────────────────── Other-Crop-Seed Contamination ──────────────

(deftest other-crop-seed-exceeded-test
  (testing "contamination within tolerance returns false (no violation)"
    (is (false? (registry/other-crop-seed-exceeded? 0.3 0.5))))

  (testing "contamination at tolerance returns false"
    (is (false? (registry/other-crop-seed-exceeded? 0.5 0.5))))

  (testing "contamination exceeding tolerance returns true (violation)"
    (is (true? (registry/other-crop-seed-exceeded? 0.8 0.5)))))

;; ──────────────────────── Germinator Calibration ──────────────────────

(deftest germinator-calibration-overdue-test
  (testing "recent calibration returns false (no violation)"
    ;; Assume calibrated 10 days ago
    (let [now #?(:clj (System/currentTimeMillis) :cljs (.now js/Date))
          ten-days-ago (- now (* 10 24 60 60 1000))]
      (is (false? (registry/germinator-calibration-overdue? ten-days-ago now)))))

  (testing "overdue calibration returns true (violation)"
    (let [now #?(:clj (System/currentTimeMillis) :cljs (.now js/Date))
          hundred-days-ago (- now (* 100 24 60 60 1000))]
      (is (true? (registry/germinator-calibration-overdue? hundred-days-ago now))))))

;; ──────────────────────── Weight Variance ──────────────────────

(deftest weight-variance-excessive-test
  (testing "variance within tolerance returns false (no violation)"
    (is (false? (registry/weight-variance-excessive? 45 50))))

  (testing "variance at tolerance returns false"
    (is (false? (registry/weight-variance-excessive? 50 50))))

  (testing "variance exceeding tolerance returns true (violation)"
    (is (true? (registry/weight-variance-excessive? 51 50)))))

;; ──────────────────────── Trait Labeling ──────────────────────

(deftest trait-label-risk-test
  (testing "declared traits match formulation returns false (no risk)"
    (let [formula #{:gm-bt-trait :gm-ht-trait}
          declared #{:gm-bt-trait :gm-ht-trait}]
      (is (false? (registry/trait-label-risk? formula declared)))))

  (testing "declared traits exceed formulation returns false (conservative)"
    (let [formula #{:gm-bt-trait}
          declared #{:gm-bt-trait :gm-ht-trait}]
      (is (false? (registry/trait-label-risk? formula declared)))))

  (testing "formulation trait undeclared returns true (risk)"
    (let [formula #{:gm-bt-trait :gm-ht-trait}
          declared #{:gm-bt-trait}]
      (is (true? (registry/trait-label-risk? formula declared))))))

;; ──────────────────────── Seed-Borne Disease ──────────────────────

(deftest seed-borne-disease-detected-test
  (testing "no detection returns false"
    (is (false? (registry/seed-borne-disease-detected? false)))
    (is (false? (registry/seed-borne-disease-detected? nil))))

  (testing "detection returns true"
    (is (true? (registry/seed-borne-disease-detected? true)))))

;; ──────────────────────── Sanitation Score ──────────────────────

(deftest sanitation-score-insufficient-test
  (testing "score at minimum returns false (no violation)"
    (is (false? (registry/sanitation-score-insufficient? 75 75))))

  (testing "score above minimum returns false"
    (is (false? (registry/sanitation-score-insufficient? 85 75))))

  (testing "score below minimum returns true (violation)"
    (is (true? (registry/sanitation-score-insufficient? 74 75)))))
