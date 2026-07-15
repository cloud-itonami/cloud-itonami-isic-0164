# Contributing to cloud-itonami-isic-0164

Thank you for your interest in contributing to the Seed Processing for
Propagation Operations actor.

## Scope

This repository is a specialization of the cloud-itonami architecture for ISIC
0164 (seed processing for propagation). Contributions should:

1. Extend or correct the **Governor rules** (seed-viability/quality constraints)
2. Add **seed-lot types** or **jurisdictional requirements** to the facts registry
3. Improve **test coverage** for seed-processing-specific scenarios
4. Clarify **documentation** and ADRs

## Prohibited Changes

Do **not**:

- Add direct cleaning/grading-line equipment control (scalper/gravity-table/screen/germinator operation remains exclusive to facility staff)
- Modify the Governor to allow LLM confidence to override viability/quality hard holds
- Add JVM-only code (all source must be `.cljc` / portable)
- Change the AGPL-3.0-or-later license

## Process

1. Open an issue describing your proposed change
2. Link to the relevant ADR in the `kotoba-lang/industry` registry repository (or the `com-junkawasaki/root` superproject's `90-docs/adr/`)
3. Submit a pull request against `main`
4. Ensure all tests pass: `clojure -M:test`
5. Run linter: `clojure -M:lint`

## Code Style

- Use `.cljc` for all source (no `.clj` or `.cljs` only)
- Follow Clojure conventions (kebab-case, docstrings on public fns)
- Governor rules must be pure, side-effect-free predicates
- Test all new facts and registry entries

## Questions?

File an issue or reach out to the maintainers.
