package com.thinklab.application.usecase;

import com.thinklab.domain.exception.DrAccessDeniedException;

/** The DR registry is for IT staff and for the people and tools that run exercises (ADR-032): a REQUESTER is refused everywhere. */
final class DrAccess {

    static final String REQUESTER_ROLE = "REQUESTER";

    private DrAccess() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    static void requireStaff(String role, String operation) {
        if (REQUESTER_ROLE.equals(role)) {
            throw new DrAccessDeniedException("A requester cannot " + operation + ": recovery plans are handled by IT staff.");
        }
    }
}
