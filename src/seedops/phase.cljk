(ns seedops.phase
  "Phase machine: the states a seed-processing-for-propagation batch
  transits through.

  State machine:
    :intake -> :clean -> :grade -> :test -> :package -> :audit -> :archived

  `:intake` is seed-lot receiving; `:clean` is seed cleaning/scalping
  (removing inert matter, chaff, and other debris); `:grade` is
  size/density grading (screens, gravity tables); `:test` is quality
  testing (germination/purity/moisture/other-crop-seed/seed-borne-disease);
  `:package` is finished-lot bagging and treatment (e.g. fungicide
  seed-treatment); `:audit` is compliance audit; `:archived` is the
  terminal state.

  Each transition can accept a proposal and yield an audit fact.")

(def all-phases
  "All valid phases in the seed-processing-for-propagation workflow."
  [:intake :clean :grade :test :package :audit :archived])

(def phase-sequence
  "Ordered phases representing normal batch progression."
  [:intake :clean :grade :test :package :audit :archived])

(defn valid-phase?
  "Check if a phase is valid."
  [phase]
  (contains? (set all-phases) phase))

(defn- index-of
  "Portable (Clojure/ClojureScript) index lookup -- `.indexOf` is a
  JVM-only `java.util.List` method that ClojureScript's PersistentVector
  does not implement, so it is avoided here even though `phase-sequence`
  is a plain vector. Returns -1 when `x` is not found, matching
  `java.util.List/indexOf`'s contract."
  [coll x]
  (or (first (keep-indexed (fn [i v] (when (= v x) i)) coll)) -1))

(defn can-transition?
  "Check if a transition from one phase to another is valid
  (must be forward-only in the sequence, no backtracking). Always returns a
  boolean (never nil), including when either phase is invalid."
  [from-phase to-phase]
  (boolean
   (and (valid-phase? from-phase) (valid-phase? to-phase)
        (let [from-idx (index-of phase-sequence from-phase)
              to-idx (index-of phase-sequence to-phase)]
          (and (>= from-idx 0) (>= to-idx 0) (< from-idx to-idx))))))
