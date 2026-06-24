package com.richardsand.novelkms.model;

/**
 * Factory-default page layout — the SYSTEM row in the {@code BOOK -> PROJECT ->
 * SYSTEM} resolution. Lazily seeded by {@code PageLayoutDao} (no row exists
 * until first resolved), mirroring how {@code EditorSettingsDefaults} seeds the
 * SYSTEM editor-settings row.
 *
 * <p>Values match the former {@code book} column defaults (V3): page layout off,
 * US Letter, 1in top/bottom/outer margins and 1.25in inner (binding) margin.
 */
public final class PageLayoutDefaults {

    private PageLayoutDefaults() {}

    public static final double DEFAULT_MARGIN_TOP    = 1.0;
    public static final double DEFAULT_MARGIN_BOTTOM = 1.0;
    public static final double DEFAULT_MARGIN_INNER  = 1.25;
    public static final double DEFAULT_MARGIN_OUTER  = 1.0;

    /** A fresh PageLayout carrying the factory values (no scope/ids set). */
    public static PageLayout defaults() {
        return PageLayout.builder()
                .pageLayoutEnabled(false)
                .pageSizePreset("LETTER")
                .pageWidthIn(null)
                .pageHeightIn(null)
                .pageMarginTopIn(DEFAULT_MARGIN_TOP)
                .pageMarginBottomIn(DEFAULT_MARGIN_BOTTOM)
                .pageMarginInnerIn(DEFAULT_MARGIN_INNER)
                .pageMarginOuterIn(DEFAULT_MARGIN_OUTER)
                .build();
    }
}
