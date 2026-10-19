# ADR-031: The Promise Is Judged When Read

## Status
Accepted

## Context
Whether a plan holds depends on the clock and on the last exercises, not on any event: an exercise that is not run is not a message. Storing a verdict would need a job to keep it true.

## Decision
- **Nothing about the verdict is stored.** `readiness`, `nextExerciseDueAt`, `rtoMet` and `rpoMet` are computed from the plan and its summary at the moment of each read, like the promises of the backup, patch and capacity registries.
- **Readiness is the first of these that applies**: `PAUSED` (not judged); `LAST_EXERCISE_FAILED` (the latest exercise was partial or failed, whatever passed before it: the plan is not proved); `OBJECTIVE_MISSED` (the last exercise that passed took longer than `rtoMinutes`, or lost more than `rpoMinutes`); `TEST_OVERDUE` (an exercise falls due `testEveryDays` after the last one that passed, or after the plan was created, and that moment is past); `NEVER_TESTED` (no exercise has passed yet, and the first interval is not over); `READY`.
- **A later passing exercise clears an earlier failure**, because the summary remembers the latest exercise and its outcome.
- **A missed objective comes before an overdue exercise**: it is a known defect, while overdue is only a lack of proof.
- **RTO and RPO are met when equal**: `rtoMet` compares the recovery time of the last pass with `rtoMinutes`, `rpoMet` its data loss with `rpoMinutes`. Both are unknown (null) until a pass exists, and `rpoMet` stays unknown when the exercise did not report what it lost; unknown does not count as missed.
- **A plan cannot promise what its strategy cannot do**: a plan that restores from a backup cannot promise a recovery point of zero (a standby strategy can). Every number has a ceiling: minutes up to a year, the exercise interval up to a year.
- The list is filterable by status, asset and readiness; the readiness filter is applied in memory over the tenant's plans because it is judged when read.

## Consequences
- Positive: always true at the moment of reading, nothing to repair after a restart or a clock change, no scheduler.
- Negative: the list reads every plan of the tenant and judges it (cheap for the expected size, a limit for very large tenants). Telling someone that a plan stopped being ready needs a reader: pairing this with the alerting service is a later slice. The registry cannot tell whether the recovery time reported is honest.
