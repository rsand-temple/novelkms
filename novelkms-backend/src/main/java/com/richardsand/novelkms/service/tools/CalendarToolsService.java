package com.richardsand.novelkms.service.tools;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

public class CalendarToolsService {

    public record DayOfWeekResult(
            String date,
            String calendar,
            String dayOfWeek,
            int isoDayOfWeek,
            String note) {
    }

    private record DateParts(int year, int month, int day) {
    }

    public DayOfWeekResult dayOfWeek(String dateText, String calendarText) {
        String calendar = normalizeCalendar(calendarText);

        if ("julian".equals(calendar)) {
            DateParts parts = parseDateParts(dateText);
            int       iso   = julianIsoDayOfWeek(parts.year(), parts.month(), parts.day());
            return new DayOfWeekResult(
                    dateText,
                    "julian",
                    displayName(iso),
                    iso,
                    "Julian calendar calculation. Historical civil calendars varied by country and adoption date.");
        }

        LocalDate date = LocalDate.parse(dateText);
        int       iso  = date.getDayOfWeek().getValue();
        return new DayOfWeekResult(
                date.toString(),
                "gregorian",
                date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                iso,
                "Proleptic Gregorian calculation. Historical dates before local Gregorian adoption may differ.");
    }

    private static String normalizeCalendar(String calendarText) {
        if (calendarText == null || calendarText.isBlank())
            return "gregorian";
        String value = calendarText.trim().toLowerCase(Locale.ROOT);
        if ("gregorian".equals(value) || "julian".equals(value))
            return value;
        throw new IllegalArgumentException("Calendar must be gregorian or julian.");
    }

    private static DateParts parseDateParts(String dateText) {
        try {
            String[] parts = dateText.split("-");
            if (parts.length != 3)
                throw new IllegalArgumentException();
            int year  = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day   = Integer.parseInt(parts[2]);
            LocalDate.of(year, month, Math.min(day, 28)); // validates year/month shape enough before range check
            int maxDay = java.time.Month.of(month).length(isJulianLeapYear(year));
            if (day < 1 || day > maxDay)
                throw new IllegalArgumentException();
            return new DateParts(year, month, day);
        } catch (Exception e) {
            throw new IllegalArgumentException("Enter a date as YYYY-MM-DD.");
        }
    }

    private static boolean isJulianLeapYear(int year) {
        return Math.floorMod(year, 4) == 0;
    }

    private static int julianIsoDayOfWeek(int year, int month, int day) {
        int a   = (14 - month) / 12;
        int y   = year + 4800 - a;
        int m   = month + 12 * a - 3;
        int jdn = day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
        return Math.floorMod(jdn, 7) + 1; // ISO: Monday=1 ... Sunday=7
    }

    private static String displayName(int isoDayOfWeek) {
        return switch (isoDayOfWeek) {
        case 1 -> "Monday";
        case 2 -> "Tuesday";
        case 3 -> "Wednesday";
        case 4 -> "Thursday";
        case 5 -> "Friday";
        case 6 -> "Saturday";
        case 7 -> "Sunday";
        default -> throw new IllegalArgumentException("Invalid ISO day of week: " + isoDayOfWeek);
        };
    }
}
