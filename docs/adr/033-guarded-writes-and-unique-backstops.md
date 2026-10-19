# ADR-033: Guarded Writes, and Unique Indexes as the Atomic Backstops

## Status
Accepted

## Context
Several instances serve the registry, staff edit plans while exercises are reported, and reporters retry. Checking first and writing later is not atomic.

## Decision
- Every change to an existing plan is **one atomic update guarded by the status that was loaded**, which also appends the audit entry. A lost race is 409 (`ERR-DRR-00409`): read again and decide again.
- **A plan name is unique per organisation** (`(organisationId, name)`), on create and on rename; the index is the atomic backstop of a duplicate.
- **One exercise per `(organisationId, planId, kind, startedAt)`** is a unique index. The loser of a simultaneous insert reads the stored exercise and answers it as not created.
- The summary of the exercises is written by two guarded updates, each only when the stored value is empty or older: the last exercise and its outcome, and (for a pass) the last pass with its recovery time and data loss. Order and repetition of reports do not matter.
- Indexes are created at startup, fail-open and idempotent; `thinklab.mongo.create-indexes=false` turns them off. A third index `(organisationId, planId, startedAt desc)` serves the real exercise query.
- The mongo adapters set the update time themselves and never write what the domain did not ask for.

## Consequences
- Positive: with N instances and a retrying reporter there is still exactly one exercise per start and no lost updates.
- Negative: without the index (it is fail-open) a simultaneous double report could store two exercises; the startup log says when an index could not be created. The summary and the exercise are separate writes: a crash between them is repaired by the retry (ADR-030).
