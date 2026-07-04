package com.richardsand.novelkms.model.editor;

/**
 * Factory-default document settings — the values seeded into the single SYSTEM
 * {@code editor_settings} row on first fetch (lazy seed, mirroring
 * {@code StyleDefaults} / {@code TemplateDao.getOrCreateGlobal}).
 *
 * <p>These match the values that previously lived in the frontend
 * {@code PROJECT_SETTINGS_DEFAULTS} constant, so an existing user's editor looks
 * identical the first time settings are resolved from the server. They are
 * editable thereafter (USER override) and per-project (PROJECT override).
 */
public final class EditorSettingsDefaults {

    private EditorSettingsDefaults() {}

    public static EditorSettingsDefinition defaults() {
        return EditorSettingsDefinition.builder()
                .fontFamily("Georgia, serif")
                .fontSize("1.0625rem")
                .lineHeight("1.9")
                .firstLineIndent("1.5em")
                .spacingAfter("0.9em")
                .sceneBreakStyle("* * *")
                .sceneBreakSpacingAbove("2em")
                .sceneBreakSpacingBelow("2em")
                .build();
    }
}
