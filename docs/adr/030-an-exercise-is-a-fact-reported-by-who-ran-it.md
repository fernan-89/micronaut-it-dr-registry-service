# ADR-030: An Exercise Is a Fact, Reported by Whoever Ran It

## Status
Accepted

## Context
A disaster recovery plan is worth what the last exercise proved. Exercises are run by people and tools that the registry does not control (an engineer walking through a runbook, a failover job, a cloud service). The registry has to say whether the promise made about an asset holds, without holding a credential, reaching an asset or running anything.

## Decision
- **The registry only records.** A `RecoveryPlan` is the promise: the strategy (`BACKUP_RESTORE`, `PILOT_LIGHT`, `WARM_STANDBY`, `ACTIVE_ACTIVE`), the recovery time objective in minutes, the recovery point objective in minutes, how often it must be proved, and optionally where the runbook is. A `RecoveryExercise` is what is reported when an exercise of the plan ends (`exercise/initiate`), of one of three kinds: `TABLETOP` (a walk through the runbook), `FAILOVER` (a real switch to the standby) or `FULL_RECOVERY` (a recovery from nothing). Nothing here schedules, starts or reads an exercise.
- **An exercise is a fact**: immutable, never edited, never deleted. Its outcome is `SUCCEEDED`, `PARTIAL` or `FAILED`; the last two carry one reason from a fixed list (`RUNBOOK_OUTDATED`, `DEPENDENCY_MISSING`, `ACCESS_PROBLEM`, `DATA_LOSS_EXCEEDED`, `TIMEOUT`, `OTHER`), never the reporter's own words, which could hold hostnames or credentials. A successful exercise has none. It may say how much data it lost (`dataLossMinutes`, 0 to a year), when that is known.
- **The runbook is a reference, not text.** `runbookRef` is an identifier (a knowledge-base article id, a document number, a path-like reference: letters, digits and `. _ : / # -`, up to 64 characters, no spaces), so a sentence, a command or a password cannot be stored in it.
- **Reporting is idempotent**: the same plan, kind and start is the same exercise (unique index on `(organisationId, planId, kind, startedAt)`, times cut to the millisecond). A repeat, or twenty at once, answers the stored exercise with 200 instead of 201, and no sovereign id is spent for it.
- **The reporter's clock may be a little off**: an exercise may finish at most 5 minutes after the registry's clock, never before it started.
- **Only some exercises measure.** A `TABLETOP` is a walk through the runbook: it takes as long as the meeting did, which says nothing about how long a recovery takes. A `FAILOVER` and a `FULL_RECOVERY` measure: their duration (whole minutes, rounded up, at least one) is the recovery time, and they may say how much data was lost.
- **The plan keeps a summary** of its exercises: the last exercise and its outcome, the last one that passed (of any kind), and the last passed one that measured, with how long the recovery took and how much data it lost. Each part is written only when the exercise finished later than the one remembered, so late or repeated reports change nothing they should not. A report that stored the exercise and died before the summary was updated is repaired by the retry, because the summary is folded again even for an exercise already stored.

## Consequences
- Positive: no credentials, no access to assets, nothing to secure on the target side; anyone who can call an HTTP route can report.
- Negative: the registry trusts what is reported. A plan nobody exercises is seen as overdue (ADR-031), which is the point; an exercise that never happened cannot be caught here.
