package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.model.Book;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.Style;
import com.richardsand.novelkms.model.StyleDefaults;
import com.richardsand.novelkms.model.StyleDefinition;

class StyleDaoTest extends NovelKmsTestBase {

    private Project project;
    private Book book;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        project = projectDao.create("Test Project", null);
        book = bookDao.create(project.getId(), "Book One", null, null, null);
    }

    private StyleDefinition sampleDef(String fontSize) {
        return StyleDefinition.builder()
                .fontFamily("Georgia, serif").fontSize(fontSize)
                .bold(true).italic(false)
                .firstLineIndent("0").textIndent("0")
                .spacingBefore("0").spacingAfter("1em")
                .build();
    }

    // ── Global: lazy seed + round-trip ─────────────────────────────────────────

    @Test
    void getOrCreateGlobal_lazilySeedsFromDefaultAndRoundTripsJson() throws SQLException {
        assertTrue(styleDao.findGlobal(StyleDefaults.NORMAL).isEmpty());

        Style s = styleDao.getOrCreateGlobal(StyleDefaults.NORMAL);

        assertNotNull(s.getId());
        assertEquals(StyleDao.SCOPE_GLOBAL, s.getScope());
        assertNotNull(s.getDefinition());
        // normal default: first-line indent 1.5em, not bold
        assertEquals("1.5em", s.getDefinition().getFirstLineIndent());
        assertFalse(s.getDefinition().isBold());
        assertEquals("Georgia, serif", s.getDefinition().getFontFamily());
    }

    @Test
    void getAllGlobal_seedsEntireRosterInOrder() throws SQLException {
        var all = styleDao.getAllGlobal();
        assertEquals(StyleDefaults.STYLE_KEYS.size(), all.size());
        for (int i = 0; i < StyleDefaults.STYLE_KEYS.size(); i++) {
            assertEquals(StyleDefaults.STYLE_KEYS.get(i), all.get(i).getStyleKey());
        }
    }

    @Test
    void partTitleDefaultsMatchChapterTitleValues() throws SQLException {
        StyleDefinition part    = styleDao.getOrCreateGlobal(StyleDefaults.PART_TITLE).getDefinition();
        StyleDefinition chapter = styleDao.getOrCreateGlobal(StyleDefaults.CHAPTER_TITLE).getDefinition();
        assertEquals(chapter.getFontSize(), part.getFontSize());
        assertEquals(chapter.isBold(), part.isBold());
        assertEquals(chapter.getSpacingAfter(), part.getSpacingAfter());
    }

    @Test
    void updateGlobal_persistsAndReusesRow() throws SQLException {
        Style seeded  = styleDao.getOrCreateGlobal(StyleDefaults.REPORT);
        Style updated = styleDao.updateGlobal(StyleDefaults.REPORT, sampleDef("0.8rem"));
        assertEquals(seeded.getId(), updated.getId());
        assertEquals("0.8rem", updated.getDefinition().getFontSize());
    }

    @Test
    void resetGlobal_restoresDefault() throws SQLException {
        styleDao.updateGlobal(StyleDefaults.REPORT, sampleDef("0.8rem"));
        Style reset = styleDao.resetGlobal(StyleDefaults.REPORT);
        // report default is Courier, 0.95rem
        assertEquals("0.95rem", reset.getDefinition().getFontSize());
    }

    // ── Cascade resolution ─────────────────────────────────────────────────────

    @Test
    void resolveForBook_fallsBackToGlobal() throws SQLException {
        Style s = styleDao.resolveForBook(book.getId(), StyleDefaults.NORMAL);
        assertEquals(StyleDao.SCOPE_GLOBAL, s.getScope());
    }

    @Test
    void resolveForBook_prefersProjectOverGlobal() throws SQLException {
        styleDao.upsertProjectOverride(project.getId(), StyleDefaults.NORMAL, sampleDef("1.1rem"));
        Style s = styleDao.resolveForBook(book.getId(), StyleDefaults.NORMAL);
        assertEquals(StyleDao.SCOPE_PROJECT, s.getScope());
        assertEquals("1.1rem", s.getDefinition().getFontSize());
    }

    @Test
    void resolveForBook_prefersBookOverProjectAndGlobal() throws SQLException {
        styleDao.upsertProjectOverride(project.getId(), StyleDefaults.NORMAL, sampleDef("1.1rem"));
        styleDao.upsertBookOverride(book.getId(), StyleDefaults.NORMAL, sampleDef("1.3rem"));
        Style s = styleDao.resolveForBook(book.getId(), StyleDefaults.NORMAL);
        assertEquals(StyleDao.SCOPE_BOOK, s.getScope());
        assertEquals("1.3rem", s.getDefinition().getFontSize());
    }

    @Test
    void resolveForProject_prefersProjectOverGlobal() throws SQLException {
        styleDao.upsertProjectOverride(project.getId(), StyleDefaults.H1, sampleDef("2.2rem"));
        Style s = styleDao.resolveForProject(project.getId(), StyleDefaults.H1);
        assertEquals(StyleDao.SCOPE_PROJECT, s.getScope());
        assertEquals("2.2rem", s.getDefinition().getFontSize());
    }

    // ── Overrides: upsert / delete ─────────────────────────────────────────────

    @Test
    void upsertBookOverride_secondCallUpdatesSameRow() throws SQLException {
        Style first  = styleDao.upsertBookOverride(book.getId(), StyleDefaults.H1, sampleDef("2rem"));
        Style second = styleDao.upsertBookOverride(book.getId(), StyleDefaults.H1, sampleDef("2.5rem"));
        assertEquals(first.getId(), second.getId());
        assertEquals("2.5rem", second.getDefinition().getFontSize());
    }

    @Test
    void deleteBookOverride_revertsToProjectThenGlobal() throws SQLException {
        styleDao.upsertProjectOverride(project.getId(), StyleDefaults.NORMAL, sampleDef("1.1rem"));
        styleDao.upsertBookOverride(book.getId(), StyleDefaults.NORMAL, sampleDef("1.3rem"));

        assertTrue(styleDao.deleteBookOverride(book.getId(), StyleDefaults.NORMAL));
        Style afterBookDelete = styleDao.resolveForBook(book.getId(), StyleDefaults.NORMAL);
        assertEquals(StyleDao.SCOPE_PROJECT, afterBookDelete.getScope());

        assertTrue(styleDao.deleteProjectOverride(project.getId(), StyleDefaults.NORMAL));
        Style afterProjectDelete = styleDao.resolveForBook(book.getId(), StyleDefaults.NORMAL);
        assertEquals(StyleDao.SCOPE_GLOBAL, afterProjectDelete.getScope());
    }

    @Test
    void deleteBookOverride_whenNoneExists_returnsFalse() throws SQLException {
        assertFalse(styleDao.deleteBookOverride(book.getId(), StyleDefaults.NORMAL));
    }

    @Test
    void overrideDoesNotMutateGlobal() throws SQLException {
        styleDao.upsertBookOverride(book.getId(), StyleDefaults.NORMAL, sampleDef("1.3rem"));
        Style global = styleDao.getOrCreateGlobal(StyleDefaults.NORMAL);
        assertEquals("1.5em", global.getDefinition().getFirstLineIndent());
        assertEquals("1rem", global.getDefinition().getFontSize());
    }

    // ── Resolved stylesheets ───────────────────────────────────────────────────

    @Test
    void resolveAllForBook_returnsFullRosterWithMixedScopes() throws SQLException {
        styleDao.upsertBookOverride(book.getId(), StyleDefaults.REPORT, sampleDef("0.7rem"));
        var sheet = styleDao.resolveAllForBook(book.getId());

        assertEquals(StyleDefaults.STYLE_KEYS.size(), sheet.size());
        Style report = sheet.stream()
                .filter(s -> s.getStyleKey().equals(StyleDefaults.REPORT)).findFirst().orElseThrow();
        assertEquals(StyleDao.SCOPE_BOOK, report.getScope());
        Style normal = sheet.stream()
                .filter(s -> s.getStyleKey().equals(StyleDefaults.NORMAL)).findFirst().orElseThrow();
        assertEquals(StyleDao.SCOPE_GLOBAL, normal.getScope());
    }
}
