package com.thinklab.domain.exception;

/** Domain Exception: an illegal RecoveryPlan lifecycle transition, or the plan changed while the write was applied. RFC 7807 mapping: HTTP 409 Conflict. */
public class InvalidRecoveryPlanStatusException extends BusinessException {

    private static final String ERROR_CODE = "ERR-DRR-00409";

    public InvalidRecoveryPlanStatusException(String message) {
        super(ERROR_CODE, message);
    }
}
