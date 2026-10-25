package io.atomity.blogcms.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @Test
    void slugifiesLikeTheFrontend() {
        assertThat(Slugs.slugify("Hello, Wörld!")).isEqualTo("hello-world");
        assertThat(Slugs.slugify("  --Already--kebab--  ")).isEqualTo("already-kebab");
        assertThat(Slugs.slugify("EU Data Act: 2027")).isEqualTo("eu-data-act-2027");
        assertThat(Slugs.slugify("!!!")).isEmpty();
        assertThat(Slugs.slugify(null)).isEmpty();
    }

    @Test
    void capsLengthWithoutTrailingHyphen() {
        String slug = Slugs.slugify("a".repeat(119) + " b" + "c".repeat(50));
        assertThat(slug).hasSizeLessThanOrEqualTo(120).doesNotEndWith("-");
    }

    @Test
    void slugifyWithMaxLengthCapsAtThatLength() {
        assertThat(Slugs.slugify("a".repeat(100), 80)).isEqualTo("a".repeat(80));
        assertThat(Slugs.slugify("Short title", 80)).isEqualTo("short-title");
        // NFKD turns "½" into "1⁄2", so the slug would be longer than the input without the cap.
        assertThat(Slugs.slugify("½".repeat(58), 80)).hasSizeLessThanOrEqualTo(80).matches(Slugs.PATTERN);
    }

    @Test
    void slugifyWithMaxLengthDropsTheHyphenLeftByTheCut() {
        // "abcd-efgh" cut at 5 is "abcd-".
        assertThat(Slugs.slugify("abcd efgh", 5)).isEqualTo("abcd");
    }

    @Test
    void slugifyWithoutMaxLengthStillCapsAt120() {
        String input = "a".repeat(119) + " b" + "c".repeat(50);
        assertThat(Slugs.slugify(input)).isEqualTo("a".repeat(119)).isEqualTo(Slugs.slugify(input, 120));
    }
}
