package com.trustdesk.common;

import java.util.UUID;

public final class Ids {
    private Ids() {}

    /** Short unique id such as "run_1a2b3c4d" (not time-based; never used for policy). */
    public static String next(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
