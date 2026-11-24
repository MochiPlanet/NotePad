package com.example.android.notepad;

import android.content.Context;
import android.graphics.Color;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Small helpers shared by the note list, the editor and the adapter.
 * They keep formatting (time, categories, colors) in one place.
 */
public final class NoteUtils {

    /** Categories shown in the list filter bar and in the editor. */
    public static final String[] CATEGORIES = new String[] {
            NotePad.Notes.CATEGORY_DEFAULT,
            NotePad.Notes.CATEGORY_WORK,
            NotePad.Notes.CATEGORY_STUDY,
            NotePad.Notes.CATEGORY_LIFE
    };

    /** Pseudo category used by the list when no filter is active. */
    public static final String ALL_CATEGORIES = "全部";

    private static final int COLOR_DEFAULT = 0xFF4A6CF7;
    private static final int COLOR_WORK = 0xFFF59E0B;
    private static final int COLOR_STUDY = 0xFF10B981;
    private static final int COLOR_LIFE = 0xFFEC4899;

    private NoteUtils() {
    }

    /** Returns the accent color of a category. */
    public static int categoryColor(String category) {
        if (NotePad.Notes.CATEGORY_WORK.equals(category)) {
            return COLOR_WORK;
        }
        if (NotePad.Notes.CATEGORY_STUDY.equals(category)) {
            return COLOR_STUDY;
        }
        if (NotePad.Notes.CATEGORY_LIFE.equals(category)) {
            return COLOR_LIFE;
        }
        return COLOR_DEFAULT;
    }

    /** Makes sure a category value read from the database is one we know about. */
    public static String normalizeCategory(String category) {
        for (String item : CATEGORIES) {
            if (item.equals(category)) {
                return item;
            }
        }
        return NotePad.Notes.CATEGORY_DEFAULT;
    }

    /** Index of a category inside {@link #CATEGORIES}. */
    public static int categoryIndex(String category) {
        String normalized = normalizeCategory(category);
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equals(normalized)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Mixes a color with white. {@code amount} is how much of {@code color} remains
     * (1 = the color itself, 0.08 = a very light tint).
     */
    public static int mixWithWhite(int color, float amount) {
        float keep = Math.max(0f, Math.min(1f, amount));
        int red = (int) (Color.red(color) * keep + 255 * (1 - keep));
        int green = (int) (Color.green(color) * keep + 255 * (1 - keep));
        int blue = (int) (Color.blue(color) * keep + 255 * (1 - keep));
        return Color.rgb(red, green, blue);
    }

    /**
     * Formats the timestamp shown under every note:
     * "今天 09:30", "昨天 18:02", "3月8日 14:20" or "2024年3月8日".
     */
    public static String formatListTime(long millis) {
        if (millis <= 0) {
            return "";
        }
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(millis);

        if (isSameDay(now, target)) {
            return "今天 " + format(millis, "HH:mm");
        }

        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(yesterday, target)) {
            return "昨天 " + format(millis, "HH:mm");
        }

        if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR)) {
            return format(millis, "M月d日 HH:mm");
        }
        return format(millis, "yyyy年M月d日");
    }

    /** Full timestamp used by the editor header and by the exported text file. */
    public static String formatFullTime(long millis) {
        if (millis <= 0) {
            return "";
        }
        return format(millis, "yyyy-MM-dd HH:mm");
    }

    /** Timestamp used in exported file names. */
    public static String formatFileStamp(long millis) {
        return format(millis, "yyyyMMdd_HHmm");
    }

    private static String format(long millis, String pattern) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.getDefault());
        return formatter.format(new Date(millis));
    }

    private static boolean isSameDay(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Builds the one or two line preview shown in a list row: line breaks become spaces,
     * repeated whitespace is collapsed and very long notes are shortened.
     */
    public static String snippet(String content) {
        if (content == null) {
            return "";
        }
        String text = content.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() > 120) {
            text = text.substring(0, 120).trim() + "…";
        }
        return text;
    }

    /**
     * Escapes the wildcards used by SQL LIKE so that searching for "%" or "_" behaves
     * like a normal text search.
     */
    public static String escapeLike(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Converts dp to pixels. */
    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
