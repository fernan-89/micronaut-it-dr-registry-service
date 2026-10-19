# micronaut-it-dr-registry-service

BIAN-aligned Service Domain **it-dr-registry** (Control Record: `RecoveryPlan`), port `8112`.

The last slice of Journey 14 (operations): a **registry of disaster recovery plans**. A `RecoveryPlan` is the promise made about recovering one
asset (how it is meant to come back, how long it may stay down, how much data it may lose, how often the plan must be proved). A
`RecoveryExercise` is what a person or tool reports when an exercise of the plan ends. The registry says, **when you ask**, whether each promise
holds and whether it has been proved lately.

## What it guarantees, and what it does not

- **It only records** (ADR-030): whoever runs the exercise reports it (`exercise/initiate`). The registry never starts, schedules or runs an
  exercise, holds no credential and reaches no asset.
- **An exercise is a fact** (ADR-030): never edited, never deleted. Its outcome is `SUCCEEDED`, `PARTIAL` or `FAILED`; the last two carry one reason
  from a fixed list (`RUNBOOK_OUTDATED`, `DEPENDENCY_MISSING`, `ACCESS_PROBLEM`, `DATA_LOSS_EXCEEDED`, `TIMEOUT`, `OTHER`), never the reporter's own
  text. It may say how much data it lost (`dataLossMinutes`). An exercise may finish at most 5 minutes ahead of the registry's clock.
- **The runbook is an identifier, not text** (ADR-030): `runbookRef` takes an article id, a document number or a path-like reference (letters, digits
  and `. _ : / # -`, up to 64 characters, no spaces).
- **Reporting is idempotent** (ADR-030, ADR-033): the same plan, kind and start is the same exercise. A repeat, or 20 at once, answers the stored
  exercise with 200 (a new one is 201); the reporter may retry as often as it likes.
- **The promise is judged when read** (ADR-031), never stored: `readiness` is `READY`, `NEVER_TESTED` (still inside the first interval),
  `TEST_OVERDUE`, `OBJECTIVE_MISSED` (the last measured pass was slower than the recovery time, or lost more than the recovery point), `LAST_EXERCISE_FAILED`
  (the latest exercise was partial or failed) or `PAUSED` (not judged). `rtoMet` and `rpoMet` compare the last **measured** pass with the objectives and
  are `null` until there is one (and `rpoMet` until it reported its data loss). Only a `FAILOVER` or a `FULL_RECOVERY` measures: a `TABLETOP` (a walk
  through the runbook) counts as a pass for the schedule but takes as long as the meeting did, so it says nothing about the recovery time. A later pass
  clears an earlier failure.
- **A plan cannot promise what its strategy cannot do** (ADR-031): a plan that restores from a backup (`BACKUP_RESTORE`) cannot promise a recovery
  point of zero; `PILOT_LIGHT`, `WARM_STANDBY` and `ACTIVE_ACTIVE` can.
- **Staff only** (ADR-032): every route refuses a `REQUESTER` with 403 `ERR-DRR-00403`; another tenant's plan or exercise answers 404.
  No free text from a reporter is kept, and there is no delete (a plan is paused).
- **Safe with several instances** (ADR-033): a plan name is unique per organisation, one exercise per `(plan, kind, start)` is a unique index,
  every change is guarded by the status loaded, and the summary of the exercises only moves forward in time whatever the order of the reports.
- **Not here yet:** telling someone when a plan stops being ready (a later pairing with alerting), the order in which several assets must come
  back, dependencies between plans, running or scheduling exercises, and a link from the runbook reference to the knowledge base.

## BIAN Behavior Qualifier Contract

`X-Tenant-Id` is mandatory on every call; `X-Executor` is mandatory on the actions (for an exercise, the name of the person or tool); `X-Role` is
optional and, with platform security on, comes from the verified token.

| Behavior Qualifier | Route |
|---|---|
| initiate | `POST /it-dr-registry/v1/initiate` `{"name":"Orders failover","assetId":"<uuid>","strategy":"WARM_STANDBY","rtoMinutes":120,"rpoMinutes":30,"testEveryDays":90,"runbookRef":"KB-1042"}` (`runbookRef` is optional; `strategy`: `BACKUP_RESTORE`, `PILOT_LIGHT`, `WARM_STANDBY`, `ACTIVE_ACTIVE`) |
| retrieve | `GET /it-dr-registry/v1/{id}/retrieve` (judged now) |
| retrieve (collection) | `GET /it-dr-registry/v1/retrieve?status=&assetId=&readiness=` (oldest first) |
| update | `PUT /it-dr-registry/v1/{id}/update` (the whole definition again, in any status) |
| control/pause | `PUT /it-dr-registry/v1/{id}/control/pause` (ACTIVE -> PAUSED: no longer judged) |
| control/resume | `PUT /it-dr-registry/v1/{id}/control/resume` (PAUSED -> ACTIVE) |
| audit-log/retrieve | `GET /it-dr-registry/v1/{id}/audit-log/retrieve` |
| exercise/initiate | `POST /it-dr-registry/v1/exercise/initiate` `{"planId":"<uuid>","kind":"FAILOVER","outcome":"SUCCEEDED","startedAt":"<instant>","finishedAt":"<instant>","dataLossMinutes":5}` (`kind`: `TABLETOP`, `FAILOVER`, `FULL_RECOVERY`; `dataLossMinutes` optional; `failureReason` mandatory with `PARTIAL` or `FAILED` and forbidden with `SUCCEEDED`) |
| exercise/retrieve | `GET /it-dr-registry/v1/exercise/{id}/retrieve` |
| exercise/retrieve (collection) | `GET /it-dr-registry/v1/exercise/retrieve?planId=&kind=&outcome=&limit=` (newest start first, 50 unless asked, at most 200) |

```text
plan:     ACTIVE <-> PAUSED
exercise: a fact (TABLETOP, FAILOVER or FULL_RECOVERY; SUCCEEDED, PARTIAL or FAILED), never changes
```

```bash
curl -X POST "http://localhost:8112/it-dr-registry/v1/exercise/initiate" -H "X-Tenant-Id: <organisationId>" -H "X-Executor: dr-drill-2026-q4" \
  -H "Content-Type: application/json" \
  -d '{"planId":"<planId>","kind":"FAILOVER","outcome":"SUCCEEDED","startedAt":"2026-10-07T02:00:00Z","finishedAt":"2026-10-07T02:41:00Z","dataLossMinutes":5}'
```

## Error catalog

| Code | HTTP | Meaning |
|---|---|---|
| `ERR-DRR-00403` | 403 | A REQUESTER used the DR registry (ADR-032) |
| `ERR-DRR-00404` | 404 | Plan or exercise not found (another tenant's answers the same) |
| `ERR-DRR-00409` | 409 | Duplicate plan name, illegal transition (pause a paused plan), or it changed while the write was applied (retry) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure, a backup-restore plan promising no data loss, a runbook reference that is not an identifier, an exercise that finishes before it starts or in the future, a partial or failed exercise without a reason |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

## Configuration

`HASH_SERVICE_URL` (default `http://localhost:8080`, the sovereign ids), `MICRONAUT_SERVER_PORT` (default 8112), `mongodb.uri`,
`thinklab.mongo.create-indexes` (default on). The service calls no other service and has no scheduler.

Do not put personal data in a plan name: it is stored with the plan.

## Architecture decisions

001 hexagonal architecture · 005 UUID identity sovereignty and audit tracing · 013 BIAN conventions · 019 HTTP 409 for state conflicts ·
030 an exercise is a fact reported by whoever ran it · 031 the promise is judged when read · 032 staff only · 033 guarded writes and unique backstops.

## License

Licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read and use this software for noncommercial purposes only. Modifying it, creating derivative works, redistributing it and any commercial use are not permitted without a separate written license. This software is not open source.
