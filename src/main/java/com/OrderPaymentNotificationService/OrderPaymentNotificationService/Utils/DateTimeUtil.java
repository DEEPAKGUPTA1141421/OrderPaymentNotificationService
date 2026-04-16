package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Central date/time utility for the OrderPaymentNotificationService.
 *
 * All business logic in this service operates in IST (Asia/Kolkata, UTC+5:30).
 * Use these helpers instead of scattering {@code ZoneId.of("Asia/Kolkata")} and
 * {@code DateTimeFormatter.ofPattern(...)} across every class.
 *
 * Usage examples:
 * <pre>
 *   ZonedDateTime now        = DateTimeUtil.nowIst();
 *   LocalDate     today      = DateTimeUtil.todayIst();
 *   ZonedDateTime yesterday  = DateTimeUtil.hoursAgoIst(24);
 *   ZonedDateTime startOfDay = DateTimeUtil.startOfTodayIst();
 *   ZonedDateTime expiry     = DateTimeUtil.plusDaysIst(365);
 *   Instant       bookingExp = DateTimeUtil.plusMinutesInstant(5);
 *   String        invoiceKey = DateTimeUtil.invoiceMonthKey();   // "202501"
 *   String        display    = DateTimeUtil.formatDisplayDate(instant); // "15 Jan 2025"
 * </pre>
 */
public final class DateTimeUtil {

    // ── Timezone ──────────────────────────────────────────────────────────────

    /** Indian Standard Time — the single source of truth for this service. */
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // ── Formatters ────────────────────────────────────────────────────────────

    /** Invoice month key used in invoice numbers, e.g. {@code 202501}. */
    public static final DateTimeFormatter INVOICE_MONTH_FMT =
            DateTimeFormatter.ofPattern("yyyyMM");

    /** Human-readable date for invoice / receipt headers, e.g. {@code 15 Jan 2025}. */
    public static final DateTimeFormatter DISPLAY_DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    /** Human-readable datetime with timezone, e.g. {@code 15 Jan 2025, 03:45 PM IST}. */
    public static final DateTimeFormatter DISPLAY_DATETIME_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a z");

    /** Card expiry format used in SavedPaymentMethod validation, e.g. {@code 12/2027}. */
    public static final DateTimeFormatter CARD_EXPIRY_FMT =
            DateTimeFormatter.ofPattern("MM/yyyy");

    // ── "Now" helpers ─────────────────────────────────────────────────────────

    /** Current date-time in IST. */
    public static ZonedDateTime nowIst() {
        return ZonedDateTime.now(IST);
    }

    /** Today's date in IST. */
    public static LocalDate todayIst() {
        return LocalDate.now(IST);
    }

    /** Current local date-time in IST (for APIs that require {@link LocalDateTime}). */
    public static LocalDateTime localNowIst() {
        return LocalDateTime.now(IST);
    }

    /** Current UTC {@link Instant}. */
    public static Instant nowInstant() {
        return Instant.now();
    }

    // ── Offset helpers ────────────────────────────────────────────────────────

    /** IST midnight of the current day (useful for daily-cap queries). */
    public static ZonedDateTime startOfTodayIst() {
        return nowIst().withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    /** IST timestamp {@code hours} ago from now. */
    public static ZonedDateTime hoursAgoIst(long hours) {
        return nowIst().minusHours(hours);
    }

    /** IST timestamp {@code days} in the future from now (e.g. for loyalty point expiry). */
    public static ZonedDateTime plusDaysIst(long days) {
        return nowIst().plusDays(days);
    }

    /** UTC {@link Instant} {@code minutes} in the future (e.g. for booking hold expiry). */
    public static Instant plusMinutesInstant(long minutes) {
        return Instant.now().plus(minutes, ChronoUnit.MINUTES);
    }

    // ── Conversion helpers ────────────────────────────────────────────────────

    /** Converts a UTC {@link Instant} to an IST {@link ZonedDateTime}. */
    public static ZonedDateTime toIst(Instant instant) {
        return instant.atZone(IST);
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    /**
     * Returns the current year-month key used in invoice numbers.
     * Example: {@code "202501"} for January 2025.
     */
    public static String invoiceMonthKey() {
        return todayIst().format(INVOICE_MONTH_FMT);
    }

    /**
     * Formats an {@link Instant} as a display date in IST.
     * Example: {@code "15 Jan 2025"}.
     */
    public static String formatDisplayDate(Instant instant) {
        return toIst(instant).format(DISPLAY_DATE_FMT);
    }

    /**
     * Formats an {@link Instant} as a full display datetime in IST.
     * Example: {@code "15 Jan 2025, 03:45 PM IST"}.
     */
    public static String formatDisplayDatetime(Instant instant) {
        return toIst(instant).format(DISPLAY_DATETIME_FMT);
    }

    // ── No instances ─────────────────────────────────────────────────────────

    private DateTimeUtil() {
        throw new UnsupportedOperationException("Utility class");
    }
}
