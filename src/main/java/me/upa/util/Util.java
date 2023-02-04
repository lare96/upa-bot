package me.upa.util;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class Util {

    public static String capitalize(String str) {
        if (str.length() > 0) {
            str = str.trim().toLowerCase();
            char cap = Character.toUpperCase(str.charAt(0));
            return cap + str.substring(1);
        }
        return str;
    }

    public static String computeDuration(Instant instant)  {
        String finishedIn;
        Instant now = Instant.now();
        long daysUntil = now.until(instant, ChronoUnit.DAYS);
        long hoursUntil = now.until(instant, ChronoUnit.HOURS);
        long minutesUntil = now.until(instant, ChronoUnit.MINUTES);
        if (daysUntil > 0) {
            finishedIn = daysUntil + " day(s)";
        } else if (hoursUntil > 0) {
            finishedIn = hoursUntil + " hour(s)";
        } else if (minutesUntil > 0) {
            finishedIn = minutesUntil + " minute(s)";
        } else {
            finishedIn = "A few moments";
        }
        return finishedIn;
    }

    private Util() {
    }
}
