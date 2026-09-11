(ns seedops.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [seedops.phase :as phase]))

;; ──────────────────────── Phase Validity ──────────────────────

(deftest valid-phase-test
  (testing "intake is valid"
    (is (true? (phase/valid-phase? :intake))))

  (testing "grade is valid"
    (is (true? (phase/valid-phase? :grade))))

  (testing "archived is valid"
    (is (true? (phase/valid-phase? :archived))))

  (testing "invalid phase returns false"
    (is (false? (phase/valid-phase? :invalid)))))

;; ──────────────────────── Phase Transitions ──────────────────────

(deftest can-transition-test
  (testing "intake -> clean is valid (forward progression)"
    (is (true? (phase/can-transition? :intake :clean))))

  (testing "intake -> grade is valid (skip clean)"
    (is (true? (phase/can-transition? :intake :grade))))

  (testing "clean -> intake is invalid (backward)"
    (is (false? (phase/can-transition? :clean :intake))))

  (testing "grade -> archived is valid (forward to end)"
    (is (true? (phase/can-transition? :grade :archived))))

  (testing "archived -> intake is invalid (backward from end)"
    (is (false? (phase/can-transition? :archived :intake))))

  (testing "same phase is invalid"
    (is (false? (phase/can-transition? :grade :grade))))

  (testing "invalid phases return false"
    (is (false? (phase/can-transition? :invalid :grade)))
    (is (false? (phase/can-transition? :grade :invalid)))))
