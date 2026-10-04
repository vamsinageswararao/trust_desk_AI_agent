package com.trustdesk.common;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/** ISO-8601 text helpers. Offsets are preserved so policy dates can be evaluated in the ticket's own offset. */
public final class Dates {
    private Dates() {}

    public static String format(OffsetDateTime value) {
        return value == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value);
    }

    public static String format(LocalDate value) {
        return value == null ? null : value.toString();
    }

    public static OffsetDateTime parseOffset(String value) {
        return value == null || value.isBlank() ? null : OffsetDateTime.parse(value);
    }

    public static LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }
}
