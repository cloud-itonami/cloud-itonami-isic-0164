(ns seedops.sim
  "Simulation driver for testing the seed-processing-for-propagation
  operations actor end-to-end.

  For CLI: clojure -M:dev:run

  Example flow:
    1. Start with empty store
    2. Create a batch in :intake phase
    3. Propose a batch -> :test transition with germination/purity/moisture
       parameters
    4. Governor validates parameters against facts
    5. If valid, audit fact is committed
    6. CLI prints audit trail")

(defn -main [& _args]
  (println "SeedOps simulation: not yet implemented.")
  (println "TODO: integrate langgraph-clj StateGraph when available."))
