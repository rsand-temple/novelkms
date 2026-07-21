package com.richardsand.novelkms.util;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;

/**
 * Generates the immutable field keys used as property names inside a codex
 * entry's {@code scene.structured_data} JSON. A key is
 * {@code slug(label) + '_' + 4-hex} (e.g. {@code wingspan_7f3a}).
 *
 * <p>The key is the load-bearing safety mechanism for the whole Extensible Codex
 * feature: it is generated exactly once when a field is created and is
 * <b>never</b> regenerated on rename, reorder, or input-type change, so every
 * stored value keeps resolving no matter how the author edits the field's
 * presentation. Existing keys backfilled by V42 (e.g. {@code role},
 * {@code authorNotes}) are preserved verbatim and are not produced here.
 *
 * <p>The slug is a lowercase alphanumeric squeeze of the label (punctuation and
 * whitespace dropped), truncated so the full key fits the
 * {@code codex_type_field.field_key VARCHAR(80)} column, and falling back to
 * {@code field} when the label has no alphanumeric characters. The 4-hex suffix
 * disambiguates fields whose labels slug to the same base; a collision against
 * an existing key (checked across <em>all</em> of a Type's fields, including
 * soft-removed ones, since the unique index spans deleted rows) is resolved by
 * drawing a fresh suffix.
 */
public final class CodexFieldKeys {

    private static final SecureRandom RANDOM       = new SecureRandom();
    private static final int          MAX_SLUG     = 60;
    private static final int          MAX_ATTEMPTS = 64;
    private static final String       FALLBACK     = "field";

    private CodexFieldKeys() {
    }

    /**
     * Produces a new immutable key for {@code label} that does not collide with
     * any key in {@code existingKeys}. {@code existingKeys} may be null or empty.
     */
    public static String generate(String label, Set<String> existingKeys) {
        String slug = slug(label);
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String candidate = slug + "_" + hex4();
            if (existingKeys == null || !existingKeys.contains(candidate)) {
                return candidate;
            }
        }
        // Astronomically unlikely with a 65,536-value suffix space and a handful
        // of existing keys; a longer suffix guarantees termination regardless.
        return slug + "_" + hex4() + hex4();
    }

    /**
     * The alphanumeric slug portion of a key: lowercase, non-{@code [a-z0-9]}
     * removed, truncated to {@value #MAX_SLUG}, {@value #FALLBACK} when empty.
     */
    static String slug(String label) {
        String base = label == null
                ? ""
                : label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
        if (base.isEmpty()) {
            base = FALLBACK;
        }
        if (base.length() > MAX_SLUG) {
            base = base.substring(0, MAX_SLUG);
        }
        return base;
    }

    private static String hex4() {
        return String.format("%04x", RANDOM.nextInt(0x10000));
    }
}
