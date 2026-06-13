package com.richardsand.novelkms.service;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.poi.xwpf.usermodel.IRunElement;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.dao.BookDao;
import com.richardsand.novelkms.dao.ChapterDao;
import com.richardsand.novelkms.dao.PartDao;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.model.Book;
import com.richardsand.novelkms.model.Chapter;
import com.richardsand.novelkms.model.Part;
import com.richardsand.novelkms.model.Scene;

public class ImportService {

    private static final Logger logger = LoggerFactory.getLogger(ImportService.class);

    private final BookDao    bookDao;
    private final PartDao    partDao;
    private final ChapterDao chapterDao;
    private final SceneDao   sceneDao;

    public ImportService(BookDao bookDao, PartDao partDao, ChapterDao chapterDao, SceneDao sceneDao) {
        this.bookDao    = bookDao;
        this.partDao    = partDao;
        this.chapterDao = chapterDao;
        this.sceneDao   = sceneDao;
    }

    // -------------------------------------------------------------------------
    // Public result type
    // -------------------------------------------------------------------------

    public static class ImportResult {
        public UUID         bookId;
        public String       bookTitle;
        public int          partCount;
        public int          chapterCount;
        public int          sceneCount;
        public int          wordCount;
        public List<String> warnings = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public ImportResult importDocx(UUID projectId, String bookTitleOverride, String filename, InputStream stream)
            throws Exception {

        ImportResult result = new ImportResult();

        try (XWPFDocument doc = new XWPFDocument(stream)) {

            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            logger.info("DOCX import started: projectId={}, paragraphCount={}", projectId, paragraphs.size());

            boolean hasParts = detectParts(paragraphs, doc);
            logger.info("DOCX import structure: hasParts={}", hasParts);

            String bookTitle = resolveBookTitle(bookTitleOverride, doc, filename);

            Book book = bookDao.create(projectId, bookTitle, null, null, null);
            result.bookId    = book.getId();
            result.bookTitle = bookTitle;

            parse(paragraphs, doc, book.getId(), hasParts, result);
        }

        logger.info("DOCX import complete: parts={}, chapters={}, scenes={}, words={}",
                result.partCount, result.chapterCount, result.sceneCount, result.wordCount);
        return result;
    }

    // -------------------------------------------------------------------------
    // Structure detection
    // -------------------------------------------------------------------------

    /**
     * Returns true if the document uses a two-level heading structure where
     * Heading 1 = parts and Heading 2 = chapters. Requires at least one H1,
     * one H2, and at least one body paragraph following an H2.
     */
    private boolean detectParts(List<XWPFParagraph> paragraphs, XWPFDocument doc) {
        int     h1Count     = 0;
        int     h2Count     = 0;
        int     bodyAfterH2 = 0;
        boolean lastWasH2   = false;

        for (XWPFParagraph para : paragraphs) {
            String style = getWordStyleName(para, doc).toLowerCase();
            if (style.matches("heading\\s*1")) {
                h1Count++;
                lastWasH2 = false;
            } else if (style.matches("heading\\s*2")) {
                h2Count++;
                lastWasH2 = true;
            } else {
                String text = para.getText().trim();
                if (lastWasH2 && !text.isEmpty() && !isSceneBreakText(text)) {
                    bodyAfterH2++;
                    lastWasH2 = false;
                }
            }
        }

        return h1Count >= 1 && h2Count >= 1 && bodyAfterH2 >= 1;
    }

    // -------------------------------------------------------------------------
    // Title resolution
    // -------------------------------------------------------------------------

    private String resolveBookTitle(String override, XWPFDocument doc, String filename) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        // Try document core properties
        try {
            if (doc.getProperties() != null && doc.getProperties().getCoreProperties() != null) {
                String title = doc.getProperties().getCoreProperties().getTitle();
                if (title != null && !title.isBlank()) return title.trim();
            }
        } catch (Exception e) {
            logger.warn("Could not read document core properties title: {}", e.getMessage());
        }
        // Fall back to filename without extension
        if (filename != null && !filename.isBlank()) {
            String base = filename.replaceFirst("\\.[^.]+$", "").trim();
            if (!base.isBlank()) return base;
        }
        return "Imported Book";
    }

    // -------------------------------------------------------------------------
    // Main parser
    // -------------------------------------------------------------------------

    private void parse(List<XWPFParagraph> paragraphs, XWPFDocument doc,
                       UUID bookId, boolean hasParts, ImportResult result) throws SQLException {

        Part    currentPart    = null;
        Chapter currentChapter = null;
        Chapter preamble       = null;   // chapter for pre-heading content

        List<String> sceneParas = new ArrayList<>(); // HTML paragraphs accumulating for current scene

        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph para      = paragraphs.get(i);
            String        styleName = getWordStyleName(para, doc);
            String        text      = para.getText().trim();

            boolean isPartHeading    = hasParts && isHeadingN(styleName, 1);
            boolean isChapterHeading = hasParts ? isHeadingN(styleName, 2) : isHeadingN(styleName, 1);
            boolean isSceneBreak     = isSceneBreakText(text);
            boolean isSubtitleStyle  = styleName.toLowerCase().contains("subtitle");

            if (isPartHeading) {
                // Save whatever was accumulating
                finalizeScene(currentChapter, sceneParas, result);
                sceneParas = new ArrayList<>();

                String partTitle = text.isEmpty() ? "Part " + (result.partCount + 1) : text;
                currentPart = partDao.create(bookId, partTitle, null, null);
                result.partCount++;
                currentChapter = null;

            } else if (isChapterHeading) {
                // Save whatever was accumulating
                finalizeScene(currentChapter, sceneParas, result);
                sceneParas = new ArrayList<>();

                // Lookahead for subtitle
                String subtitle = null;
                if (i + 1 < paragraphs.size()) {
                    XWPFParagraph next          = paragraphs.get(i + 1);
                    String        nextStyleName = getWordStyleName(next, doc);
                    if (nextStyleName.toLowerCase().contains("subtitle")) {
                        subtitle = next.getText().trim();
                        if (subtitle.isBlank()) subtitle = null;
                        i++; // consume subtitle paragraph
                    }
                }

                String chapterTitle = text.isEmpty() ? "Chapter " + (result.chapterCount + 1) : text;
                UUID   partId       = currentPart != null ? currentPart.getId() : null;
                currentChapter = chapterDao.create(bookId, partId, chapterTitle, subtitle, null);
                result.chapterCount++;

            } else if (isSceneBreak) {
                // Only split if we actually have content built up
                if (currentChapter != null && !sceneParas.isEmpty()) {
                    finalizeScene(currentChapter, sceneParas, result);
                    sceneParas = new ArrayList<>();
                }
                // If no content yet, discard the scene break (e.g., break right after chapter heading)

            } else if (isSubtitleStyle) {
                // Standalone subtitle paragraph not immediately after a heading — treat as body
                if (ensureChapter(currentChapter, preamble, bookId, result) == null) {
                    // This is the first paragraph and no chapter context exists yet
                    preamble = chapterDao.create(bookId, null, "Preamble", null, null);
                    result.chapterCount++;
                    currentChapter = preamble;
                    result.warnings.add("Paragraphs found before first heading — placed in a 'Preamble' chapter.");
                }
                String html = buildParagraphHtml(para, styleName, hasParts);
                if (html != null) {
                    sceneParas.add(html);
                    result.wordCount += countWords(text);
                }

            } else {
                // Body content paragraph
                if (text.isEmpty() && sceneParas.isEmpty()) continue; // skip leading blanks

                // Ensure we have a chapter
                if (currentChapter == null) {
                    if (preamble == null) {
                        preamble = chapterDao.create(bookId, null, "Preamble", null, null);
                        result.chapterCount++;
                        result.warnings.add("Paragraphs found before first heading — placed in a 'Preamble' chapter.");
                    }
                    currentChapter = preamble;
                }

                String html = buildParagraphHtml(para, styleName, hasParts);
                if (html != null) {
                    sceneParas.add(html);
                    result.wordCount += countWords(text);
                }
            }
        }

        // Finalize any remaining accumulated content
        finalizeScene(currentChapter, sceneParas, result);

        // Guard: if the document yielded no chapters at all, create a placeholder
        if (result.chapterCount == 0) {
            chapterDao.create(bookId, null, "Chapter 1", null, null);
            result.chapterCount = 1;
            result.warnings.add("No chapter headings were detected — created one empty chapter. "
                    + "Make sure chapter headings use a Heading style in Word.");
        }
    }

    // -------------------------------------------------------------------------
    // Scene finalization
    // -------------------------------------------------------------------------

    /**
     * Writes the accumulated paragraph list as a scene under the given chapter.
     * Does nothing if chapter is null. Always creates at least an empty scene
     * if the chapter has no scenes yet (checked via sceneParas being non-empty
     * or explicitly called at chapter transitions).
     */
    private void finalizeScene(Chapter chapter, List<String> htmlParas, ImportResult result)
            throws SQLException {
        if (chapter == null || htmlParas.isEmpty()) return;

        String content   = String.join("\n", htmlParas);
        int    wordCount = countWords(stripHtml(content));

        Scene scene = sceneDao.create(chapter.getId(), "", null);
        sceneDao.saveContent(scene.getId(), content, wordCount);
        result.sceneCount++;
    }

    // Helper used in the subtitle-style branch
    private Chapter ensureChapter(Chapter current, Chapter preamble, UUID bookId, ImportResult result) {
        if (current != null) return current;
        return preamble;
    }

    // -------------------------------------------------------------------------
    // HTML generation
    // -------------------------------------------------------------------------

    /**
     * Converts a Word paragraph to an HTML string compatible with TipTap's
     * document model. Returns null for paragraphs that should be silently skipped
     * (e.g., table of contents entries).
     *
     * Inline formatting preserved: bold, italic, underline, strikethrough.
     * Inline font sizes are NOT imported — the style cascade governs sizing.
     */
    private String buildParagraphHtml(XWPFParagraph para, String styleName, boolean hasParts) {
        String lower    = styleName.toLowerCase().trim();
        String tag      = "p";
        String styleKey = null;

        // Heading paragraphs that reach this method are sub-chapter-level headings
        // (H2 in no-parts mode becomes h2, H3 always becomes h3, etc.)
        if (!hasParts && lower.matches("heading\\s*2")) {
            tag = "h2";
        } else if (lower.matches("heading\\s*3")) {
            tag = "h3";
        } else if (lower.matches("heading\\s*4") || lower.matches("heading\\s*5") || lower.matches("heading\\s*6")) {
            tag = "h3"; // collapse deep headings to h3
        } else if (lower.matches("block.*(text|quotation|quote)?")
                || lower.equals("quote")
                || lower.equals("blockquote")
                || lower.equals("quotation")) {
            styleKey = "blockquote";
        } else if (lower.equals("emphasis")) {
            styleKey = "emphasis";
        }
        // Normal / Body Text / etc. → plain <p> with no data-style

        StringBuilder sb = new StringBuilder();
        sb.append("<").append(tag);
        if (styleKey != null) {
            sb.append(" data-style=\"").append(styleKey).append("\"");
        }
        sb.append(">");

        // getIRuns() returns all run elements in document order, including
        // XWPFHyperlinkRun (which extends XWPFRun), so one loop handles both.
        for (IRunElement runElement : para.getIRuns()) {
            if (runElement instanceof XWPFRun run) {
                String runHtml = buildRunHtml(run);
                if (!runHtml.isEmpty()) {
                    sb.append(runHtml);
                }
            }
        }

        sb.append("</").append(tag).append(">");
        return sb.toString();
    }

    private String buildRunHtml(XWPFRun run) {
        String text = run.getText(0);
        if (text == null || text.isEmpty()) return "";

        text = escapeHtml(text);

        // Apply inline marks. Order matters: innermost wrap applied first so
        // outermost wrapper ends up on the outside.
        boolean isStrike    = run.isStrikeThrough();
        boolean isItalic    = run.isItalic();
        boolean isBold      = run.isBold();
        boolean isUnderline = run.getUnderline() != null && run.getUnderline() != UnderlinePatterns.NONE;

        if (isStrike)    text = "<s>" + text + "</s>";
        if (isItalic)    text = "<em>" + text + "</em>";
        if (isBold)      text = "<strong>" + text + "</strong>";
        if (isUnderline) text = "<u>" + text + "</u>";

        return text;
    }

    // -------------------------------------------------------------------------
    // Style and structure helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a human-readable style name for the paragraph. Looks up the
     * named style from the document's style table so locale-specific style IDs
     * (e.g., "berschrift1" in German Word) resolve to normalized names like
     * "Heading 1".
     */
    private String getWordStyleName(XWPFParagraph para, XWPFDocument doc) {
        String styleId = para.getStyleID();
        if (styleId == null) return "Normal";

        XWPFStyles styles = doc.getStyles();
        if (styles != null) {
            XWPFStyle style = styles.getStyle(styleId);
            if (style != null) {
                String name = style.getName();
                if (name != null && !name.isBlank()) return name;
            }
        }
        // Fallback: CamelCase ID to "Camel Case"
        return styleId.replaceAll("([A-Z])", " $1").trim();
    }

    private boolean isHeadingN(String styleName, int n) {
        return styleName.toLowerCase().trim().matches("heading\\s*" + n);
    }

    private boolean isSceneBreakText(String text) {
        if (text == null) return false;
        String t = text.trim();
        // Common scene-break patterns authors use
        return t.matches("\\*+\\s*\\*+\\s*\\*+")   // * * *, ***, ** ** **, etc.
            || t.equals("#")
            || t.matches("-{3,}")                  // --- or longer
            || t.equals("—")
            || t.equals("–")
            || t.equals("· · ·")
            || t.equals("• • •")
            || t.equals("~")
            || t.matches("~+\\s*~+\\s*~+");       // ~ ~ ~
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ");
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }
}