package io.atomity.blogcms.shared;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Lowercase-kebab slugs shared by posts, authors and tags. Mirrors the frontend's lib/slug.ts. */
public final class Slugs {

    public static final String PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";
    public static final String MESSAGE = "may only contain lowercase letters, numbers and single hyphens";

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final int MAX_LENGTH = 120;

    private Slugs() {
    }

    public static String slugify(String input) {
        return slugify(input, MAX_LENGTH);
    }

    /**
     * Like {@link #slugify(String)}, but cut to at most {@code maxLength} characters, so a
     * derived slug fits its column. NFKD can make the slug longer than the input
     * ("½" becomes "1-2").
     */
    public static String slugify(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        String ascii = DIACRITICS.matcher(Normalizer.normalize(input, Normalizer.Form.NFKD)).replaceAll("");
        String slug = NON_ALPHANUMERIC.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = trimHyphens(slug);
        if (slug.length() > maxLength) {
            slug = trimHyphens(slug.substring(0, maxLength));
        }
        return slug;
    }

    private static String trimHyphens(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }
}
