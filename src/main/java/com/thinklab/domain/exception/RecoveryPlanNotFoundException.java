package com.thinklab.domain.exception;

/** Domain Exception: no recovery plan with that id in the tenant (another tenant's answers the same). RFC 7807 mapping: HTTP 404 Not Found. */
public class RecoveryPlanNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "ERR-DRR-00404";

    public RecoveryPlanNotFoundException(String message) {
        super(ERROR_CODE, message);
    }
}
