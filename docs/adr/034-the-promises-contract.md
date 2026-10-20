# ADR-034: The Promises Contract

## Status
Accepted. Implements platform ADR-001 (`thinklab-platform/docs/adr/001-the-promises-contract.md`).

## Context
The alerting service used to know this registry's fields and verdicts (which states are a broken promise, and the code of each). A rule that lived in a consumer was untested with the domain and had to be kept in step by hand.

## Decision
- The registry answers `GET /it-dr-registry/v1/promises/retrieve?changedWithinDays=&limit=` as the platform contract says: every broken promise and every one that changed within the window (30 days unless asked, 1 to 365), at most `limit` of each (500 unless asked, 1 to 1000), broken first.
- **The judgement lives here:** a plan is **broken** when its latest exercise failed or was partial (`DR_EXERCISE_FAILED`), its last measured pass missed the recovery time or point objective (`DR_OBJECTIVE_MISSED`) or its next exercise is overdue (`DR_TEST_OVERDUE`), **kept** when ready, and **not judged** when never exercised yet (still inside its first interval) or paused. The name, the asset and the status are the plan's.
- **`changedAt`:** the latest of the plan's last update, last exercise and last pass.
- Staff only, tenant-scoped, read-only.

## Consequences
- Positive: the rule is judged and tested with the domain; the consumer needs no knowledge of this registry.
- Negative: a pass reported late about an old moment will not move `changedAt` forward; the plan is still listed while it is broken.
