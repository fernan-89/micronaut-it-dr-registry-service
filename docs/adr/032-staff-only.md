# ADR-032: The DR Registry Is for IT Staff and Their Tools

## Status
Accepted

## Context
Plans name assets and how well they would survive a disaster; exercises name when and why a recovery failed. A requester (end user) has no business reading them.

## Decision
- Every route refuses a `REQUESTER` with 403 `ERR-DRR-00403`; any other role, or none, is staff (the same rule as the monitor, the alerting service and the other registries). The people and tools that report exercises are staff or service identities, which are not requesters.
- Every lookup is scoped by `X-Tenant-Id`: another tenant's plan or exercise is a 404, never a 403.
- **There is no delete.** A plan is paused (its history stays), an exercise is a fact.
- Audit is kept on the plan only, for real events (initiated, updated, paused, resumed), with the executor id. Exercises are not audited: they are the record themselves, with `reportedBy`.
- **Nothing a reporter says is kept as text**: no message, path or log line, only the fixed failure reason, times, the data lost and the executor.

## Consequences
- Positive: one access rule across the operations services; no free text that could leak a secret.
- Negative: a failed exercise says why only in one of six words; the detail stays in the reporter's own notes.
