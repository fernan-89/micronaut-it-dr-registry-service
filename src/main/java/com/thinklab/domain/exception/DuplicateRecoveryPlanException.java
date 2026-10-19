package com.thinklab.domain.exception;

/** Domain Exception: a recovery plan name already used in the organisation. RFC 7807 mapping: HTTP 409 Conflict. */
public class DuplicateRecoveryPlanException extends BusinessException {

    private static final String ERROR_CODE = "ERR-DRR-00409";

    public DuplicateRecoveryPlanException(String message) {
        super(ERROR_CODE, message);
    }
}
