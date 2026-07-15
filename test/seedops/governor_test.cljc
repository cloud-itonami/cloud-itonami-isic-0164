(ns seedops.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [seedops.governor :as governor]))

(def ^:private now-ms #?(:clj (System/currentTimeMillis) :cljs (.now js/Date)))
(def ^:private ten-days-ago (- now-ms (* 10 24 60 60 1000)))
(def ^:private hundred-days-ago (- now-ms (* 100 24 60 60 1000)))

(def ^:private clean-batch
  {:seed-lot-type :maize/hybrid
   :jurisdiction :jp/maff
   :moisture-percent 13.0
   :germination-percent 95
   :purity-percent 99.0
   :other-crop-seed-percent 0.2
   :seed-borne-disease-detected? false
   :germinator-last-calibration-date ten-days-ago
   :weight-variance-grams 20
   :seed-sources [:maize/hybrid-bt]
   :declared-traits #{:gm-bt-trait}
   :sanitation-score 85
   :evidence-checklist [:seed-lot-intake-record :cleaning-grading-log :germination-test
                        :purity-test :moisture-test :seed-borne-disease-test :trait-declaration :weight-check]})

;; ──────────────────────── Hard Violations ──────────────────────

(deftest spec-basis-violation-test
  (testing "proposal with no jurisdiction citation is a hard violation"
    (let [req {:op :log-processing-batch :subject "batch-001"}
          prop {:cites [] :value {:jurisdiction nil}}
          result (governor/check req {:actor-id "gov-1"} prop {})]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :no-spec-basis) (:violations result)))))

  (testing "proposal with proper citation passes spec basis check"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff}}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))))

;; ──────────────────────── Moisture Violations ──────────────────────

(deftest moisture-violation-test
  (testing "batch with moisture out of range triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :moisture-percent 11.0)}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :moisture-out-of-target) (:violations result)))))

  (testing "batch with moisture in range passes"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))))

;; ──────────────────────── Germination Rate Violations ──────────────────────

(deftest germination-rate-violation-test
  (testing "batch with germination rate below the seed-lot type's minimum triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :germination-percent 80)}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :germination-rate-below-minimum) (:violations result)))))

  (testing "tomato seed lot has a much lower germination floor than hybrid maize"
    (let [batch-id "batch-002"
          store {:batches {batch-id (assoc clean-batch
                                            :seed-lot-type :vegetable/tomato
                                            :moisture-percent 7.0
                                            :purity-percent 97.5
                                            :other-crop-seed-percent 0.5
                                            :germination-percent 78)}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))))

;; ──────────────────────── Purity Violations ──────────────────────

(deftest purity-violation-test
  (testing "batch with purity below the seed-lot type's minimum triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :purity-percent 90.0)}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :purity-below-minimum) (:violations result))))))

;; ──────────────────────── Other-Crop-Seed Violations ──────────────────────

(deftest other-crop-seed-violation-test
  (testing "batch with other-crop-seed contamination exceeding tolerance triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :other-crop-seed-percent 2.0)}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :other-crop-seed-exceeded) (:violations result))))))

;; ──────────────────────── Seed-Borne Disease Violations ──────────────────────

(deftest seed-borne-disease-violation-test
  (testing "batch with detected seed-borne disease triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :seed-borne-disease-detected? true)}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :seed-borne-disease-detected) (:violations result))))))

;; ──────────────────────── Germinator Calibration Violations ──────────────────────

(deftest germinator-calibration-violation-test
  (testing "batch with overdue germinator calibration triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :germinator-last-calibration-date hundred-days-ago)}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :germinator-calibration-overdue) (:violations result))))))

;; ──────────────────────── Weight Variance Violations ──────────────────────

(deftest weight-variance-violation-test
  (testing "batch with excessive weight variance triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :weight-variance-grams 75)}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :weight-variance-excessive) (:violations result))))))

;; ──────────────────────── Trait Labeling Violations ──────────────────────

(deftest trait-label-violation-test
  (testing "batch with undeclared traits triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch
                                            :seed-sources [:maize/hybrid-bt :soybean/roundup-ready]
                                            :declared-traits #{:gm-bt-trait})}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :trait-label-mismatch) (:violations result))))))

;; ──────────────────────── Sanitation Score Violations ──────────────────────

(deftest sanitation-score-violation-test
  (testing "batch with insufficient sanitation score triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch :sanitation-score 60)}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :sanitation-score-insufficient) (:violations result))))))

;; ──────────────────────── Quality Flag Violations ──────────────────────

(deftest quality-flag-unresolved-violation-test
  (testing "batch with an unresolved quality flag triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id (assoc clean-batch
                                            :quality-concern-raised? true
                                            :quality-concern-resolved? false)}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :quality-flag-unresolved) (:violations result)))))

  (testing "batch with a resolved quality flag does not trigger this rule"
    (let [batch-id "batch-002"
          store {:batches {batch-id (assoc clean-batch
                                            :quality-concern-raised? true
                                            :quality-concern-resolved? true)}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (not (some #(= (:rule %) :quality-flag-unresolved) (:violations result)))))))

;; ──────────────────────── Escalation (Low Confidence) ──────────────────────

(deftest low-confidence-escalation-test
  (testing "low confidence proposal escalates even when hard checks pass"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :schedule-maintenance :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.5}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── High Stakes Escalation ──────────────────────

(deftest high-stakes-escalation-test
  (testing "log-processing-batch escalates even when all checks pass"
    (let [batch-id "batch-001"
          store {:batches {batch-id clean-batch}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.95}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── Already Processed Violation ──────────────────────

(deftest already-processed-violation-test
  (testing "batch already processed triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id
                           {:seed-lot-type :maize/hybrid
                            :processed? true}}}
          req {:op :log-processing-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/maff} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :already-processed) (:violations result))))))
