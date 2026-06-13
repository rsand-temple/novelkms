package com.richardsand.novelkms.service;

import java.io.InputStream;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.xwpf.usermodel.IRunElement;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.dao.BookDao;
import com.richardsand.novelkms.dao.ChapterDao;
import com.richardsand.novelkms.dao.PartDao;
import com.richardsand.novelkms.dao.ProjectDao;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.model.Chapter;
import com.richardsand.novelkms.model.Part;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.Scene;

public class ImportService {

    private static final Logger logger = LoggerFactory.getLogger(ImportService.class);

    private final BookDao    bookDao;
    private final PartDao    partDao;
    private final ChapterDao chapterDao;
    private final SceneDao   sceneDao;
    private final ProjectDao projectDao;

    public ImportService(BookDao bookDao, PartDao partDao, ChapterDao chapterDao,
            SceneDao sceneDao, ProjectDao projectDao) {
        this.bookDao = bookDao;
        this.partDao = partDao;
        this.chapterDao = chapterDao;
        this.sceneDao = sceneDao;
        this.projectDao = projectDao;
    }

    // =========================================================================
    // Public result type
    // =========================================================================

    public static class ImportResult {
        public UUID         bookId;
        public String       bookTitle;
        public int          partCount;
        public int          chapterCount;
        public int          sceneCount;
        public int          wordCount;
        public boolean      coverImageImported;
        public boolean      authorUpdated;
        public List<String> warnings = new ArrayList<>();
    }

    // =========================================================================
    // Cover page extraction result
    // =========================================================================

    private static class CoverPageResult {
        String  title;
        String  subtitle;
        String  authorFirst;
        String  authorLast;
        byte[]  coverImageBytes;
        String  coverImageMimeType;
        boolean isCoverPage;
    }

    // =========================================================================
    // Entry point
    // =========================================================================

    public ImportResult importDocx(UUID projectId, String bookTitleOverride,
            String filename, InputStream stream) throws Exception {
        ImportResult result = new ImportResult();

        try (XWPFDocument doc = new XWPFDocument(stream)) {
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            logger.info("DOCX import started: projectId={}, paragraphs={}", projectId, paragraphs.size());

            CoverPageResult coverPage = extractCoverPage(paragraphs, doc);
            logger.info("DOCX import: isCoverPage={}, detectedTitle={}", coverPage.isCoverPage, coverPage.title);

            // Title priority: user override > cover page detection > doc properties > filename
            String bookTitle;
            if (bookTitleOverride != null && !bookTitleOverride.isBlank()) {
                bookTitle = bookTitleOverride.trim();
            } else if (coverPage.title != null) {
                bookTitle = coverPage.title;
            } else {
                bookTitle = resolveBookTitle(null, doc, filename);
            }

            // Create book — subtitle from cover page if detected (and not a byline)
            com.richardsand.novelkms.model.Book book = bookDao.create(projectId, bookTitle, coverPage.subtitle, null, null);
            result.bookId = book.getId();
            result.bookTitle = bookTitle;

            // Record provenance
            bookDao.setImportMetadata(book.getId(), filename, Instant.now());

            // Store cover image
            if (coverPage.coverImageBytes != null) {
                bookDao.setCoverImage(book.getId(), coverPage.coverImageBytes, coverPage.coverImageMimeType);
                result.coverImageImported = true;
                logger.info("DOCX import: cover image stored ({} bytes, {})",
                        coverPage.coverImageBytes.length, coverPage.coverImageMimeType);
            }

            // Update project author if currently blank
            if (coverPage.authorFirst != null || coverPage.authorLast != null) {
                try {
                    Optional<Project> proj = projectDao.findById(projectId);
                    if (proj.isPresent()) {
                        Project p          = proj.get();
                        boolean firstBlank = p.getAuthorFirstName() == null || p.getAuthorFirstName().isBlank();
                        boolean lastBlank  = p.getAuthorLastName() == null || p.getAuthorLastName().isBlank();
                        if (firstBlank && lastBlank) {
                            projectDao.update(projectId, p.getTitle(), p.getDescription(),
                                    coverPage.authorFirst, coverPage.authorLast, p.getCopyright());
                            result.authorUpdated = true;
                            logger.info("DOCX import: project author set to '{} {}'",
                                    coverPage.authorFirst, coverPage.authorLast);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("DOCX import: could not update project author: {}", e.getMessage());
                }
            }

            parse(paragraphs, doc, book.getId(), coverPage.isCoverPage, result);
        }

        logger.info("DOCX import complete: parts={}, chapters={}, scenes={}, words={}",
                result.partCount, result.chapterCount, result.sceneCount, result.wordCount);
        return result;
    }

    // =========================================================================
    // Cover page extraction
    // =========================================================================

    /**
     * Examines paragraphs before the first H1 and extracts manuscript
     * cover-page metadata: title, subtitle, author, cover image.
     *
     * Title detection looks for any paragraph whose style name contains "title"
     * but not "heading" — matching "Novel Title", "Book Title", "Title", etc.
     *
     * "Novel Subtitle" / similar lines that begin with "by " are treated as
     * author bylines, not book subtitles.
     */
    private CoverPageResult extractCoverPage(List<XWPFParagraph> allParas, XWPFDocument doc) {
        CoverPageResult result = new CoverPageResult();

        // Collect paragraphs before the first H1
        List<XWPFParagraph> preamble = new ArrayList<>();
        for (XWPFParagraph para : allParas) {
            if (isHeadingN(getWordStyleName(para, doc), 1))
                break;
            preamble.add(para);
        }
        if (preamble.isEmpty())
            return result;

        // ── Cover image ───────────────────────────────────────────────────────
        outer: for (XWPFParagraph para : preamble) {
            for (XWPFRun run : para.getRuns()) {
                for (XWPFPicture pic : run.getEmbeddedPictures()) {
                    XWPFPictureData pd = pic.getPictureData();
                    if (pd != null && pd.getData() != null && pd.getData().length > 0) {
                        result.coverImageBytes = pd.getData();
                        result.coverImageMimeType = getMimeTypeForPicture(pd);
                        break outer;
                    }
                }
            }
        }

        // ── Text analysis ─────────────────────────────────────────────────────
        List<String> lines  = new ArrayList<>();
        List<String> styles = new ArrayList<>();
        for (XWPFParagraph para : preamble) {
            String text  = para.getText().trim();
            String style = getWordStyleName(para, doc);
            if (!text.isEmpty()) {
                lines.add(text);
                styles.add(style);
            }
        }

        boolean hasWordCount  = lines.stream().anyMatch(this::looksLikeWordCount);
        boolean hasTitleStyle = styles.stream().anyMatch(s -> isTitleStyle(s));
        boolean hasAllCaps    = lines.stream().anyMatch(this::looksLikeAllCapsTitle);

        result.isCoverPage = preamble.size() <= 15
                && (hasWordCount || hasTitleStyle || hasAllCaps || result.coverImageBytes != null);

        if (!result.isCoverPage)
            return result;

        // ── Title + subtitle ─────────────────────────────────────────────────
        // Look for a paragraph whose style contains "title" (but not "heading")
        for (int i = 0; i < styles.size(); i++) {
            if (isTitleStyle(styles.get(i))) {
                result.title = lines.get(i);
                // Check next line for subtitle — skip "by Author" bylines
                if (i + 1 < lines.size()) {
                    String  nextLine  = lines.get(i + 1);
                    String  nextStyle = styles.get(i + 1);
                    boolean isByLine  = nextLine.toLowerCase().startsWith("by ");
                    if (isSubtitleStyle(nextStyle) && !isByLine) {
                        result.subtitle = nextLine;
                    }
                }
                break;
            }
        }

        // Fallback: all-caps line
        if (result.title == null) {
            for (int i = 0; i < lines.size(); i++) {
                if (looksLikeAllCapsTitle(lines.get(i))) {
                    result.title = toTitleCase(lines.get(i));
                    if (i + 1 < lines.size()) {
                        String next = lines.get(i + 1);
                        if (!looksLikeWordCount(next) && !looksLikeName(next) && wordCount(next) <= 8
                                && !next.toLowerCase().startsWith("by ")) {
                            result.subtitle = next;
                        }
                    }
                    break;
                }
            }
        }

        // ── Author name ───────────────────────────────────────────────────────
        // 1. Document core properties creator field (most reliable)
        try {
            if (doc.getProperties() != null && doc.getProperties().getCoreProperties() != null) {
                String creator = doc.getProperties().getCoreProperties().getCreator();
                if (creator != null && !creator.isBlank()) {
                    parseAuthorInto(creator, result);
                }
            }
        } catch (Exception e) {
            logger.debug("Could not read document creator: {}", e.getMessage());
        }

        // 2. "Novel Subtitle" / subtitle-style lines beginning with "by "
        if (result.authorFirst == null && result.authorLast == null) {
            for (int i = 0; i < lines.size(); i++) {
                if (isSubtitleStyle(styles.get(i)) && lines.get(i).toLowerCase().startsWith("by ")) {
                    parseAuthorInto(lines.get(i), result);
                    break;
                }
            }
        }

        // 3. First contact-block line that looks like a personal name
        if (result.authorFirst == null && result.authorLast == null) {
            int limit = Math.min(4, lines.size());
            for (int i = 0; i < limit; i++) {
                if (looksLikeName(lines.get(i))) {
                    parseAuthorInto(lines.get(i), result);
                    break;
                }
            }
        }

        return result;
    }

    // ── Cover page style helpers ──────────────────────────────────────────────

    /** Style contains "title" but is not a heading (matches "Novel Title", "Title", etc.). */
    private boolean isTitleStyle(String style) {
        String lower = style.toLowerCase();
        return lower.contains("title") && !lower.contains("heading");
    }

    /** Style contains "subtitle" (matches "Novel Subtitle", "Subtitle", etc.). */
    private boolean isSubtitleStyle(String style) {
        return style.toLowerCase().contains("subtitle");
    }

    private boolean looksLikeWordCount(String text) {
        return text.toLowerCase().contains("word") && text.matches(".*\\d.*");
    }

    private boolean looksLikeAllCapsTitle(String text) {
        if (text.length() < 2)
            return false;
        String lettersOnly = text.replaceAll("[^a-zA-Z ]", "");
        if (lettersOnly.isBlank())
            return false;
        boolean allCaps = lettersOnly.equals(lettersOnly.toUpperCase());
        int     words   = text.trim().split("\\s+").length;
        return allCaps && words >= 1 && words <= 7;
    }

    /**
     * Returns true for strings that look like personal names: 2–3 words, first
     * letter capitalised, allows middle initials (e.g. "Richard A. Sand").
     */
    private boolean looksLikeName(String text) {
        String t = text.trim();
        if (t.toLowerCase().startsWith("by "))
            t = t.substring(3).trim();
        // First word: capital + letters. Subsequent words: capital + letters OR single capital + dot
        return t.matches("[A-Z][a-zA-Z'\\-]+(\\s([A-Z][a-zA-Z'\\-]*\\.?)){1,2}");
    }

    /** Parses "First Last", "First M. Last", or "by First Last" into result. */
    private void parseAuthorInto(String author, CoverPageResult result) {
        String t = author.trim();
        if (t.toLowerCase().startsWith("by "))
            t = t.substring(3).trim();
        // Handle "First\tLast" (tab-separated) as well as space-separated
        String[] parts = t.split("[\\s\\t]+");
        if (parts.length >= 2) {
            result.authorFirst = parts[0];
            result.authorLast = parts[parts.length - 1]; // handles middle names/initials
        } else if (parts.length == 1 && !parts[0].isEmpty()) {
            result.authorLast = parts[0];
        }
    }

    private String toTitleCase(String allCaps) {
        Set<String>   small = new HashSet<>(Arrays.asList(
                "a", "an", "the", "and", "but", "or", "for", "nor",
                "on", "at", "to", "by", "in", "of", "up", "as"));
        String[]      words = allCaps.toLowerCase().split("\\s+");
        StringBuilder sb    = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty())
                continue;
            if (i == 0 || !small.contains(words[i])) {
                sb.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
            } else {
                sb.append(words[i]);
            }
            if (i < words.length - 1)
                sb.append(' ');
        }
        return sb.toString();
    }

    // =========================================================================
    // Title resolution fallback
    // =========================================================================

    private String resolveBookTitle(String override, XWPFDocument doc, String filename) {
        if (override != null && !override.isBlank())
            return override.trim();
        try {
            if (doc.getProperties() != null && doc.getProperties().getCoreProperties() != null) {
                String title = doc.getProperties().getCoreProperties().getTitle();
                if (title != null && !title.isBlank())
                    return title.trim();
            }
        } catch (Exception e) {
            logger.warn("Could not read document title property: {}", e.getMessage());
        }
        if (filename != null && !filename.isBlank()) {
            String base = filename.replaceFirst("\\.[^.]+$", "").trim();
            if (!base.isBlank())
                return base;
        }
        return "Imported Book";
    }

    // =========================================================================
    // Main parser
    //
    // Parts detection is context-sensitive rather than a global pre-scan.
    // When an H1 is immediately followed by an H2 (as next significant
    // non-blank paragraph), the H1's role is determined by what comes after
    // the H2:
    //
    // H1 → H2 → H1 (next significant after H2 is another H1)
    // → "subtitle" pattern: H1 = Part, H2 = part subtitle
    //
    // H1 → H2 → body (next significant after H2 is body content)
    // → "traditional" pattern: H1 = Part, H2 = Chapter within that part
    //
    // H1 → body
    // → H1 = Chapter (no parts involved)
    // =========================================================================

    private void parse(List<XWPFParagraph> paragraphs, XWPFDocument doc,
            UUID bookId, boolean skipCoverPage, ImportResult result) throws SQLException {

        Part         currentPart    = null;
        Chapter      currentChapter = null;
        Chapter      preamble       = null;
        List<String> sceneParas     = new ArrayList<>();

        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph para      = paragraphs.get(i);
            String        styleName = getWordStyleName(para, doc);
            String        text      = para.getText().trim();

            boolean isH1         = isHeadingN(styleName, 1);
            boolean isH2         = isHeadingN(styleName, 2);
            boolean isSceneBreak = isSceneBreakText(text) || isSceneBreakStyle(styleName);

            if (isH1) {
                finalizeScene(currentChapter, sceneParas, result);
                sceneParas = new ArrayList<>();

                // Context-sensitive: look ahead to determine Part vs. Chapter
                int     nextIdx  = findNextSignificantIdx(paragraphs, i + 1, doc);
                boolean nextIsH2 = nextIdx >= 0
                        && isHeadingN(getWordStyleName(paragraphs.get(nextIdx), doc), 2);

                if (nextIsH2) {
                    // Further lookahead: what follows the H2?
                    int     afterH2Idx   = findNextSignificantIdx(paragraphs, nextIdx + 1, doc);
                    boolean afterH2IsH1  = afterH2Idx >= 0
                            && isHeadingN(getWordStyleName(paragraphs.get(afterH2Idx), doc), 1);
                    boolean afterH2IsEnd = afterH2Idx < 0;

                    if (afterH2IsH1 || afterH2IsEnd) {
                        // Pattern: H1 "Part 1" → H2 "Seoul" → H1 "Chapter 1"
                        // → H1 is a Part, H2 is the part subtitle/location
                        String partTitle    = stripPartTitle(text);
                        String partSubtitle = paragraphs.get(nextIdx).getText().trim();
                        if (partSubtitle.isBlank())
                            partSubtitle = null;
                        currentPart = partDao.create(bookId, partTitle, partSubtitle, null);
                        result.partCount++;
                        currentChapter = null;
                        i = nextIdx; // consume the H2 subtitle paragraph

                    } else {
                        // Pattern: H1 "Part 1" → H2 "Chapter 1" → body
                        // → H1 is a Part; H2s will be Chapters (handled below)
                        String partTitle = stripPartTitle(text);
                        currentPart = partDao.create(bookId, partTitle, null, null);
                        result.partCount++;
                        currentChapter = null;
                        // Do NOT consume the H2 — let the next iteration handle it as a chapter
                    }
                } else {
                    // No H2 follows: this H1 is a Chapter
                    String chapterTitle = stripChapterTitle(text);
                    UUID   partId       = currentPart != null ? currentPart.getId() : null;
                    // Check immediately-following subtitle paragraph
                    String subtitle = null;
                    if (i + 1 < paragraphs.size()) {
                        XWPFParagraph next      = paragraphs.get(i + 1);
                        String        nextStyle = getWordStyleName(next, doc);
                        if (isSubtitleStyle(nextStyle)) {
                            subtitle = next.getText().trim();
                            if (subtitle.isBlank())
                                subtitle = null;
                            i++;
                        }
                    }
                    currentChapter = chapterDao.create(bookId, partId, chapterTitle, subtitle, null);
                    result.chapterCount++;
                }

            } else if (isH2) {
                // H2 that was not consumed by the H1 lookahead above.
                // In the "traditional" structure (H1=Part, H2=Chapter) this is a chapter.
                // In "no-parts" docs an H2 can appear as in-content sub-heading.
                if (currentPart != null && currentChapter == null) {
                    // Traditional mode: H2 is a Chapter within the current Part
                    finalizeScene(currentChapter, sceneParas, result);
                    sceneParas = new ArrayList<>();
                    String chapterTitle = stripChapterTitle(text);
                    currentChapter = chapterDao.create(bookId, currentPart.getId(), chapterTitle, null, null);
                    result.chapterCount++;
                } else {
                    // In-content sub-heading — render as <h2> in the current scene
                    if (!text.isEmpty()) {
                        if (currentChapter == null && !skipCoverPage) {
                            if (preamble == null) {
                                preamble = chapterDao.create(bookId, null, "Preamble", null, null);
                                result.chapterCount++;
                                result.warnings.add("Paragraphs found before first heading — placed in a 'Preamble' chapter.");
                            }
                            currentChapter = preamble;
                        }
                        if (currentChapter != null) {
                            sceneParas.add("<h2>" + escapeHtml(text) + "</h2>");
                        }
                    }
                }

            } else if (isSceneBreak) {
                if (currentChapter != null && !sceneParas.isEmpty()) {
                    finalizeScene(currentChapter, sceneParas, result);
                    sceneParas = new ArrayList<>();
                }

            } else {
                // Body content
                if (text.isEmpty() && sceneParas.isEmpty())
                    continue;

                if (currentChapter == null) {
                    if (skipCoverPage)
                        continue; // cover page content — silently discard
                    if (preamble == null) {
                        preamble = chapterDao.create(bookId, null, "Preamble", null, null);
                        result.chapterCount++;
                        result.warnings.add("Paragraphs found before first heading — placed in a 'Preamble' chapter.");
                    }
                    currentChapter = preamble;
                }

                String html = buildParagraphHtml(para, styleName);
                if (html != null) {
                    sceneParas.add(html);
                    result.wordCount += wordCount(text);
                }
            }
        }

        finalizeScene(currentChapter, sceneParas, result);

        if (result.chapterCount == 0) {
            chapterDao.create(bookId, null, "Chapter 1", null, null);
            result.chapterCount = 1;
            result.warnings.add("No chapter headings were detected — created one empty chapter. "
                    + "Make sure chapter headings use a Heading style in Word.");
        }
    }

    // =========================================================================
    // Parse helpers
    // =========================================================================

    /**
     * Returns null (blank title) when the heading text is a generic part label
     * like "Part 1", "Part I", "Part One" — auto-numbering will render it.
     * Named parts like "The Dark Tower" are kept as-is.
     */
    private String stripPartTitle(String text) {
        if (text == null || text.isBlank())
            return "";
        String t = text.trim();
        if (t.matches("(?i)Part\\s+(\\d+|[IVXLCDM]+|one|two|three|four|five|six|seven|eight"
                + "|nine|ten|eleven|twelve)(\\s.*)?")) {
            return "";
        }
        return t;
    }

    /**
     * Returns null (blank title) when the heading starts with "Chapter" —
     * auto-numbering will render "Chapter N". Named chapters are kept as-is.
     */
    private String stripChapterTitle(String text) {
        if (text == null || text.isBlank())
            return "";
        String t = text.trim();
        if (t.toLowerCase().startsWith("chapter"))
            return "";
        return t;
    }

    /**
     * Returns the index of the next paragraph that is not a blank Normal
     * paragraph, starting from {@code startFrom}. Returns -1 if none found.
     * Used for H1 context-sensitive lookahead.
     */
    private int findNextSignificantIdx(List<XWPFParagraph> paras, int startFrom, XWPFDocument doc) {
        for (int i = startFrom; i < paras.size(); i++) {
            String text  = paras.get(i).getText().trim();
            String style = getWordStyleName(paras.get(i), doc);
            // Skip blank paragraphs in Normal / blank-line styles
            if (text.isEmpty() && style.equalsIgnoreCase("Normal"))
                continue;
            return i;
        }
        return -1;
    }

    // =========================================================================
    // Scene finalization
    // =========================================================================

    private void finalizeScene(Chapter chapter, List<String> htmlParas, ImportResult result)
            throws SQLException {
        if (chapter == null || htmlParas.isEmpty())
            return;
        String content = String.join("\n", htmlParas);
        int    wc      = wordCount(stripHtml(content));
        Scene  scene   = sceneDao.create(chapter.getId(), "", null);
        sceneDao.saveContent(scene.getId(), content, wc);
        result.sceneCount++;
    }

    // =========================================================================
    // HTML generation
    // =========================================================================

    /**
     * Converts a body paragraph to TipTap-compatible HTML. Only called for
     * paragraphs that are actual scene content — heading paragraphs used for
     * structural purposes (Parts, Chapters) never reach this method.
     */
    private String buildParagraphHtml(XWPFParagraph para, String styleName) {
        String lower    = styleName.toLowerCase().trim();
        String tag      = "p";
        String styleKey = null;

        if (lower.matches("heading\\s*2")) {
            tag = "h2";
        } else if (lower.matches("heading\\s*3")) {
            tag = "h3";
        } else if (lower.matches("heading\\s*[456]")) {
            tag = "h3"; // collapse deep headings
        } else if (lower.matches("block.*(text|quotation|quote)?")
                || lower.equals("quote")
                || lower.equals("blockquote")
                || lower.equals("quotation")) {
            styleKey = "blockquote";
        } else if (lower.equals("emphasis")) {
            styleKey = "emphasis";
        }
        // Scene, Scene First Paragraph, Normal, Author Info, etc. → plain <p>

        StringBuilder sb = new StringBuilder();
        sb.append("<").append(tag);
        if (styleKey != null)
            sb.append(" data-style=\"").append(styleKey).append("\"");
        sb.append(">");

        // getIRuns() returns runs in document order including XWPFHyperlinkRun
        for (IRunElement runElement : para.getIRuns()) {
            if (runElement instanceof XWPFRun run) {
                String runHtml = buildRunHtml(run);
                if (!runHtml.isEmpty())
                    sb.append(runHtml);
            }
        }

        sb.append("</").append(tag).append(">");
        return sb.toString();
    }

    private String buildRunHtml(XWPFRun run) {
        // Embedded pictures are processed first. In Word an image run typically
        // contains only the picture (no text), so we return immediately after
        // emitting the <img> tags. The ResizableImage TipTap extension stores
        // images as base64 data URLs; allowBase64: true is already configured.
        List<XWPFPicture> pics = run.getEmbeddedPictures();
        if (!pics.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (XWPFPicture pic : pics) {
                XWPFPictureData pd = pic.getPictureData();
                if (pd != null && pd.getData() != null && pd.getData().length > 0) {
                    String mimeType = getMimeTypeForPicture(pd);
                    String base64   = Base64.getEncoder().encodeToString(pd.getData());
                    sb.append("<img src=\"data:")
                            .append(mimeType)
                            .append(";base64,")
                            .append(base64)
                            .append("\" data-align=\"center\">");
                }
            }
            return sb.toString();
        }

        // Plain text run with optional inline formatting marks
        String text = run.getText(0);
        if (text == null || text.isEmpty())
            return "";
        text = escapeHtml(text);
        if (run.isStrikeThrough())
            text = "<s>" + text + "</s>";
        if (run.isItalic())
            text = "<em>" + text + "</em>";
        if (run.isBold())
            text = "<strong>" + text + "</strong>";
        if (run.getUnderline() != null && run.getUnderline() != UnderlinePatterns.NONE) {
            text = "<u>" + text + "</u>";
        }
        return text;
    }

    // =========================================================================
    // Style / structure helpers
    // =========================================================================

    /**
     * Resolves a human-readable style name by looking up the document's style
     * table. Handles locale-specific style IDs (e.g. German "berschrift1").
     */
    private String getWordStyleName(XWPFParagraph para, XWPFDocument doc) {
        String styleId = para.getStyleID();
        if (styleId == null)
            return "Normal";
        XWPFStyles styles = doc.getStyles();
        if (styles != null) {
            XWPFStyle style = styles.getStyle(styleId);
            if (style != null) {
                String name = style.getName();
                if (name != null && !name.isBlank())
                    return name;
            }
        }
        return styleId.replaceAll("([A-Z])", " $1").trim();
    }

    private boolean isHeadingN(String styleName, int n) {
        return styleName.toLowerCase().trim().matches("heading\\s*" + n);
    }

    private boolean isSceneBreakText(String text) {
        if (text == null)
            return false;
        String t = text.trim();
        return t.matches("\\*+\\s*\\*+\\s*\\*+")
                || t.equals("#")
                || t.matches("-{3,}")
                || t.equals("—")
                || t.equals("–")
                || t.equals("· · ·")
                || t.equals("• • •")
                || t.equals("~")
                || t.matches("~+\\s*~+\\s*~+");
    }

    /** True when the paragraph style name indicates a scene break (e.g. "Scene Break"). */
    private boolean isSceneBreakStyle(String styleName) {
        String lower = styleName.toLowerCase();
        return lower.contains("scene break") || lower.contains("scenebreak");
    }

    /** Maps POI's getPictureType() int to a MIME type string. */
    private String getMimeTypeForPicture(XWPFPictureData pd) {
        return switch (pd.getPictureType()) {
        case 5 -> "image/jpeg";
        case 6 -> "image/png";
        case 8 -> "image/gif";
        case 9 -> "image/tiff";
        case 11 -> "image/bmp";
        case 12 -> "image/svg+xml";
        default -> "image/jpeg";
        };
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String stripHtml(String html) {
        return html == null ? "" : html.replaceAll("<[^>]+>", " ");
    }

    private int wordCount(String text) {
        if (text == null || text.isBlank())
            return 0;
        return text.trim().split("\\s+").length;
    }
}