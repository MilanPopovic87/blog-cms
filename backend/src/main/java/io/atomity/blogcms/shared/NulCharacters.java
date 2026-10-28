package io.atomity.blogcms.shared;

/**
 * PostgreSQL can't store the NUL character (U+0000) in text or jsonb: the database refuses the
 * value, which surfaced as a 500. Text that reaches the database is checked against this at the
 * API boundary instead, so a NUL is a 400 like any other invalid value.
 *
 * <p>Uploaded file names drop control characters ({@link Filenames}) and Markdown files are
 * checked for NUL on upload, so neither needs this.
 */
public final class NulCharacters {

    /** For {@code @Pattern}: any text, including empty, without a NUL character. */
    public static final String NONE = "[^\\x00]*";
    public static final String MESSAGE = "must not contain NUL characters";

    private NulCharacters() {
    }

    public static boolean foundIn(String value) {
        return value != null && value.indexOf('\0') >= 0;
    }
}
