package com.thinklab.domain.exception;

/** Domain Exception: a REQUESTER tried to use the DR registry (staff only, ADR-032). RFC 7807 mapping: HTTP 403 Forbidden. */
public class DrAccessDeniedException extends BusinessException {

    private static final String ERROR_CODE = "ERR-DRR-00403";

    public DrAccessDeniedException(String message) {
        super(ERROR_CODE, message);
    }
}
