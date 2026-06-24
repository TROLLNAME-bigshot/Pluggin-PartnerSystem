package grinwin.promodawn.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class TimeUtil {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm", new Locale("ru"));
    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
    }

    public static String formatDate(long millis) {
        return DATE_FORMAT.format(new Date(millis));
    }

    public static long now() {
        return System.currentTimeMillis();
    }

    public static long daysAgoMillis(int days) {
        return now() - TimeUnit.DAYS.toMillis(days);
    }

    public static long startOfTodayMoscow() {
        java.util.Calendar cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"), new Locale("ru"));
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public static long startOfWeekMoscow() {
        java.util.Calendar cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"), new Locale("ru"));
        cal.setFirstDayOfWeek(java.util.Calendar.MONDAY);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY);
        return cal.getTimeInMillis();
    }

    public static long startOfMonthMoscow() {
        java.util.Calendar cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"), new Locale("ru"));
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}


