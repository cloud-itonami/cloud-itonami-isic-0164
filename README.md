# cloud-itonami-isic-0164: Seed Processing for Propagation Coordination Actor

**ISIC Rev. 5 0164** — Seed Processing for Propagation

A distributed actor for autonomous, compliant coordination of seed-processing-for-propagation facility operations: seed-lot intake → cleaning/scalping → grading (screens, gravity tables) → germination/purity/moisture/other-crop-seed/seed-borne-disease testing → trait labeling → certified-seed shipment logistics. Sealed LLM advisor; independent Governor enforcement; append-only audit ledger. **Not equipment control.** Scalper/gravity-table/screen/germinator operation and seed-certification authority remain exclusive to licensed seed-processing facility staff and regulators.

## Scope

This actor coordinates **facility-operations workflow** for seed processing destined for propagation (planting), not consumption -- this is what distinguishes it from grain-mill post-harvest processing (ISIC 1061):
- Processing batch logging (seed-lot intake, cleaning/grading parameters, evidence checklist)
- Equipment maintenance scheduling (scalpers, gravity tables, screens, germinators)
- Quality concern escalation (low germination rate, purity shortfall, seed-borne disease detection)
- Certified-seed shipment coordination

**Out of scope:**
- Direct cleaning/grading-line equipment control (facility staff exclusive)
- Seed-certification authority (human inspector/regulator only)
- Regulatory interpretation (proposals cite jurisdiction specifications; the Governor enforces only published requirements)

## Design

### Governor (Independent Compliance Layer)

The Governor is the separation-of-powers enforcement. It never trusts the advisor's confidence for anything viability- or compliance-relevant, and it always wins over the advisor.

- **Hard HOLD** (un-overridable):
  - Operation outside the closed allowlist (`:op-not-allowed`) — includes any proposal that would touch cleaning/grading-equipment control or seed-certification authority
  - Proposal asserting an `:effect` other than `:propose` (`:effect-not-propose`)
  - No jurisdiction citation (`:no-spec-basis`) — can't verify requirements without one
  - Evidence checklist incomplete, or the batch record isn't registered (`:evidence-incomplete`)
  - Finished seed-lot moisture outside the seed-lot type's safe storage/viability range (`:moisture-out-of-target`)
  - Germination rate below the seed-lot type's minimum required rate (`:germination-rate-below-minimum`)
  - Physical/analytic purity below the seed-lot type's minimum required window (`:purity-below-minimum`)
  - Other-crop-seed contamination exceeds the seed-lot type's tolerance (`:other-crop-seed-exceeded`)
  - Seed-borne disease detected on the batch's own laboratory screening (`:seed-borne-disease-detected`)
  - Germinator/incubator calibration overdue (`:germinator-calibration-overdue`)
  - Finished-package weight variance excessive (`:weight-variance-excessive`)
  - Trait label mismatch — declared regulated traits don't cover the seed-source formulation, including GM adventitious-presence cross-contact from a neighboring field (`:trait-label-mismatch`)
  - Facility sanitation/cross-contamination-control score insufficient (`:sanitation-score-insufficient`)
  - Unresolved quality flag (`:quality-flag-unresolved`)
  - Batch already processed / shipment already finalized (double-commit guards)
  - `:coordinate-shipment` against a batch that was never registered (`:batch-not-registered`)
- **Escalate** (human sign-off always required):
  - `:log-processing-batch` / `:coordinate-shipment` — real actuation events, always require facility-operator sign-off even when the Governor is otherwise clean
  - `:flag-quality-concern` — a quality concern (low germination, seed-borne disease, trait mismatch) is never auto-resolved by advisor confidence alone
  - Low advisor confidence (below `governor/confidence-floor`, 0.6)
- **Commit** (advisor proposal approved; Governor clean; not a mandatory-escalation op):
  - Routine, low-stakes proposals only — in this actor's current allowlist that is effectively `:schedule-maintenance` when clean

### Operations (Proposals)

Closed allowlist — the advisor may **only** ever propose these four operation types, all `:effect :propose`:

- **`:log-processing-batch`** — Log seed-lot intake → cleaning → grading → testing batch into processing records (always requires human sign-off)
- **`:schedule-maintenance`** — Propose equipment maintenance for scalpers/gravity tables/screens/germinators (routine, low risk)
- **`:flag-quality-concern`** — Surface a quality concern (e.g. low germination rate, purity shortfall, seed-borne disease detection); always escalates
- **`:coordinate-shipment`** — Finalize shipment of certified seed (always requires human sign-off)

Any proposal for an operation outside this allowlist — most importantly anything that would amount to direct cleaning/grading-line control, or seed-certification authority — is refused unconditionally by the Governor (`:op-not-allowed`), regardless of advisor confidence.

## Testing

```bash
# Run full test suite
clojure -M:test

# Check code quality
clojure -M:lint

# Run demo simulation
clojure -M:run
```

## Standalone Use

This repo is **forkable outside the workspace**. If cloning standalone (not in the kotoba-lang monorepo), override `:local/root` paths in `deps.edn`:

```clojure
{:deps {io.github.kotoba-lang/langchain {:git/url "https://github.com/kotoba-lang/langchain" :git/tag "v0.1.0"}
        io.github.kotoba-lang/langgraph {:git/url "https://github.com/kotoba-lang/langgraph" :git/tag "v0.1.0"}}}
```

## License

AGPL-3.0-or-later. Forking/contribution welcome; see `CONTRIBUTING.md`.

## Security

Report security issues to the issue tracker or private disclosure; see `SECURITY.md`.

---

Part of **cloud-itonami**: autonomous actor fleet for regulated industries. See [github.com/cloud-itonami](https://github.com/cloud-itonami).
