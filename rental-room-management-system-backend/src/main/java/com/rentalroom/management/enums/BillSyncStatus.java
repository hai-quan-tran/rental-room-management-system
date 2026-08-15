package com.rentalroom.management.enums;

/** Result of trying to push a recomputed metered extra-fee amount onto an existing bill after a meter reading is saved. */
public enum BillSyncStatus {
    /** No bill exists yet for that room+month — the amount will be applied when the bill is created. */
    NO_BILL_YET,
    /** Exactly 1 matching bill was found and its extra-fee item was updated. */
    UPDATED,
    /** A matching bill exists but has already been confirmed (locked) — amount left untouched, reading still saved. */
    SKIPPED_CONFIRMED,
    /** More than 1 bill matched the room+month (contract changed mid-month) — ambiguous, left untouched. */
    SKIPPED_AMBIGUOUS_CONTRACT
}
