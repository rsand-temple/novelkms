package com.richardsand.novelkms.service;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PDFontSupplier;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.richardsand.novelkms.dao.PageLayoutDao;
import com.richardsand.novelkms.dao.PartDao;
import com.richardsand.novelkms.dao.ProjectDao;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.dao.TemplateDao;
import com.richardsand.novelkms.dao.book.BookDao;
import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.model.PageLayout;
import com.richardsand.novelkms.model.Part;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.Template;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;

/**
 * Converts NovelKMS manuscript content (stored as TipTap HTML) into .pdf
 * files using OpenHTMLtoPDF (a CSS 2.1+ renderer over Apache PDFBox).
 *
 * <p>
 * Supports the same four export scopes as {@link ExportService}: book, part,
 * chapter, scene. Formatting deliberately mirrors {@link ExportService}'s
 * standard-manuscript-format conventions (double-spaced body text, 0.5-inch
 * first-line indent, Times New Roman 12pt) rather than the author's live
 * {@code Style} cascade, for consistency between the two export formats.
 * Page size and margins come from the book's resolved page-layout settings
 * when enabled; otherwise Letter / 1-inch margins are used, same as DOCX.
 *
 * <p>
 * Unlike the DOCX path, this service does not walk the TipTap HTML into
 * low-level formatting calls. Scene HTML is embedded directly into a styled
 * XHTML document and the renderer performs the layout, which is why images,
 * inline formatting (bold/italic/underline/strike), and lists need no special
 * handling here beyond a shared stylesheet.
 *
 * <p>
 * <b>Font note:</b> text is rendered with PDFBox's built-in Times-Roman /
 * Times-Bold / Times-Italic / Times-Bold-Italic standard fonts rather than an
 * embedded TrueType font. This requires no font files in the deployment
 * container and is guaranteed to render, but the standard PDF fonts only
 * cover WinAnsi (Latin-1-ish) characters — smart quotes, em dashes, and
 * accented Western-European letters are fine; non-Latin scripts are not.
 * DOCX export does not have this limitation, since Word substitutes fonts
 * client-side.
 */
public class PdfExportService {

    private static final Logger logger = LoggerFactory.getLogger(PdfExportService.class);

    private static final String FONT_FAMILY = "Times New Roman";

    // Default page dimensions when page layout is disabled (Letter, 1" margins)
    private static final double DEFAULT_WIDTH_IN  = 8.5;
    private static final double DEFAULT_HEIGHT_IN = 11.0;
    private static final double DEFAULT_TOP_IN    = 1.0;
    private static final double DEFAULT_BOTTOM_IN = 1.0;
    private static final double DEFAULT_INNER_IN  = 1.25;
    private static final double DEFAULT_OUTER_IN  = 1.0;

    private final BookDao       bookDao;
    private final PartDao       partDao;
    private final ChapterDao    chapterDao;
    private final SceneDao      sceneDao;
    private final ProjectDao    projectDao;
    private final TemplateDao   templateDao;
    private final PageLayoutDao pageLayoutDao;

    public PdfExportService(BookDao bookDao, PartDao partDao, ChapterDao chapterDao,
            SceneDao sceneDao, ProjectDao projectDao, TemplateDao templateDao,
            PageLayoutDao pageLayoutDao) {
        this.bookDao = bookDao;
        this.partDao = partDao;
        this.chapterDao = chapterDao;
        this.sceneDao = sceneDao;
        this.projectDao = projectDao;
        this.templateDao = templateDao;
        this.pageLayoutDao = pageLayoutDao;
    }

    // =========================================================================
    // Public result type
    // =========================================================================

    /** Byte content and suggested filename for the downloaded pdf. */
    public record ExportMeta(byte[] bytes, String filename) {
    }

    // =========================================================================
    // Entry points
    // =========================================================================

    /**
     * Exports the entire book: optional cover image page, cover title page,
     * then parts/chapters in display order, each chapter on its own page.
     */
    public ExportMeta exportBook(UUID bookId) throws Exception {
        logger.info("Starting PDF book export: bookId={}", bookId);
        Book       book   = requireBook(bookId);
        Project    project = loadProject(book);
        PageLayout layout = pageLayoutDao.resolveBook(book.getId());

        StringBuilder body     = new StringBuilder();
        boolean       hadCover = false;

        if (book.isHasCoverImage()) {
            appendCoverImage(body, book);
            hadCover = true;
        }
        appendCoverTemplate(body, book, hadCover);

        body.append("<div class=\"main\">");

        List<Part>    parts          = partDao.findByBookId(bookId);
        List<Chapter> directChapters = chapterDao.findByBookId(bookId);

        if (!parts.isEmpty()) {
            boolean firstPart = true;
            for (Part part : parts) {
                appendPartHeading(body, part, !firstPart);
                firstPart = false;
                for (Chapter ch : chapterDao.findByPartId(part.getId())) {
                    appendChapterContent(body, ch, true);
                }
            }
            for (Chapter ch : directChapters) {
                appendChapterContent(body, ch, true);
            }
        } else {
            boolean firstChapter = true;
            for (Chapter ch : directChapters) {
                appendChapterContent(body, ch, !firstChapter);
                firstChapter = false;
            }
        }

        body.append("</div>");

        String html     = wrapDocument(body.toString(), layout, runningHeaderText(book, project));
        byte[] bytes     = renderPdf(html);
        String filename  = pdfFilename(book, null);
        return new ExportMeta(bytes, filename);
    }

    /** Exports a single part: part heading, then its chapters in order. */
    public ExportMeta exportPart(UUID partId) throws Exception {
        Part       part    = requirePart(partId);
        Book       book    = requireBook(part.getBookId());
        Project    project = loadProject(book);
        PageLayout layout  = pageLayoutDao.resolveBook(book.getId());

        StringBuilder body = new StringBuilder();
        body.append("<div class=\"main\">");
        appendPartHeading(body, part, false);
        for (Chapter ch : chapterDao.findByPartId(partId)) {
            appendChapterContent(body, ch, true);
        }
        body.append("</div>");

        String html = wrapDocument(body.toString(), layout, runningHeaderText(book, project));
        byte[] bytes = renderPdf(html);

        String partLabel = (part.getTitle() != null && !part.getTitle().isBlank())
                ? part.getTitle()
                : "Part " + toRoman(part.getPartNumber());
        String filename = pdfFilename(book, partLabel);
        return new ExportMeta(bytes, filename);
    }

    /** Exports a single chapter: chapter heading then all scenes. */
    public ExportMeta exportChapter(UUID chapterId) throws Exception {
        Chapter    chapter = requireChapter(chapterId);
        Book       book    = requireBook(chapter.getBookId());
        Project    project = loadProject(book);
        PageLayout layout  = pageLayoutDao.resolveBook(book.getId());

        StringBuilder body = new StringBuilder();
        body.append("<div class=\"main\">");
        appendChapterContent(body, chapter, false);
        body.append("</div>");

        String html = wrapDocument(body.toString(), layout, runningHeaderText(book, project));
        byte[] bytes = renderPdf(html);

        String chLabel = (chapter.getTitle() != null && !chapter.getTitle().isBlank())
                ? chapter.getTitle()
                : "Chapter " + chapter.getChapterNumber();
        String filename = pdfFilename(book, chLabel);
        return new ExportMeta(bytes, filename);
    }

    /** Exports a single scene: raw content only, no heading. */
    public ExportMeta exportScene(UUID sceneId) throws Exception {
        Scene      scene   = requireScene(sceneId);
        Chapter    chapter = requireChapter(scene.getChapterId());
        Book       book    = requireBook(chapter.getBookId());
        Project    project = loadProject(book);
        PageLayout layout  = pageLayoutDao.resolveBook(book.getId());

        StringBuilder body = new StringBuilder();
        body.append("<div class=\"main\">");
        body.append(sceneHtmlFragment(scene.getContent()));
        body.append("</div>");

        String html = wrapDocument(body.toString(), layout, runningHeaderText(book, project));
        byte[] bytes = renderPdf(html);

        String sceneLabel = (scene.getTitle() != null && !scene.getTitle().isBlank())
                ? scene.getTitle()
                : null;
        String filename = pdfFilename(book, sceneLabel);
        return new ExportMeta(bytes, filename);
    }

    // =========================================================================
    // Cover image + cover template (title page)
    // =========================================================================

    private void appendCoverImage(StringBuilder body, Book book) {
        BookDao.CoverImage img;
        try {
            img = bookDao.getCoverImage(book.getId()).orElse(null);
        } catch (Exception e) {
            logger.warn("Could not load cover image for PDF export: {}", e.getMessage());
            return;
        }
        if (img == null)
            return;

        String b64 = Base64.getEncoder().encodeToString(img.data());
        String mime = img.mimeType() != null ? img.mimeType() : "image/jpeg";
        body.append("<div class=\"cover-page\">")
                .append("<img src=\"data:").append(mime).append(";base64,").append(b64).append("\">")
                .append("</div>");
    }

    /**
     * Renders the resolved COVER template (BOOK override if present,
     * otherwise the user/system default) as an XHTML title page. Token spans
     * ({@code <span data-token="TITLE">}) are replaced with live values from
     * the book and its project before embedding.
     */
    private void appendCoverTemplate(StringBuilder body, Book book, boolean pageBreakBefore) {
        try {
            Template template = templateDao.resolveForBook(book.getId(), TemplateDao.TYPE_COVER);
            String   html     = template.getContent();
            if (html == null || html.isBlank())
                return;

            String resolved = resolveTokens(html, book);
            String cls       = pageBreakBefore ? "cover-page cover-page-break" : "cover-page";
            body.append("<div class=\"").append(cls).append("\">")
                    .append(resolved)
                    .append("</div>");
        } catch (Exception e) {
            logger.warn("Could not render cover template in PDF export: {}", e.getMessage());
        }
    }

    private String resolveTokens(String html, Book book) {
        Project project = null;
        int     words   = 0;
        try {
            if (book.getProjectId() != null) {
                project = projectDao.findById(book.getProjectId()).orElse(null);
                words = projectDao.getTotalWordCount(book.getProjectId());
            }
        } catch (Exception e) {
            logger.warn("Could not load project for token resolution: {}", e.getMessage());
        }

        final Project proj     = project;
        final String  wordsStr = words > 0 ? String.format("%,d", words) : null;

        org.jsoup.nodes.Document jsoup = Jsoup.parseBodyFragment(html);
        for (Element span : jsoup.select("span[data-token]")) {
            String token = span.attr("data-token");
            String value = resolveToken(token, book, proj, wordsStr);
            span.text(value != null && !value.isBlank() ? value : "");
        }
        return jsoup.body().html();
    }

    private String resolveToken(String token, Book book, Project project, String wordCount) {
        return switch (token) {
        case "TITLE" -> book.getTitle();
        case "SUBTITLE" -> book.getSubtitle();
        case "SHORT_TITLE" -> book.getShortTitle();
        case "COPYRIGHT" -> project != null ? project.getCopyright() : null;
        case "AUTHOR_FIRST_NAME" -> project != null ? project.getAuthorFirstName() : null;
        case "AUTHOR_LAST_NAME" -> project != null ? project.getAuthorLastName() : null;
        case "DISPLAY_NAME" -> project != null ? project.getDisplayName() : null;
        case "EMAIL" -> project != null ? project.getEmailAddress() : null;
        case "PHONE" -> project != null ? project.getPhoneNumber() : null;
        case "WORDS" -> wordCount;
        case "AUTHOR_FULL_NAME" -> {
            if (project == null)
                yield null;
            String f    = project.getAuthorFirstName();
            String l    = project.getAuthorLastName();
            String full = ((f != null ? f : "") + " " + (l != null ? l : "")).trim();
            yield full.isEmpty() ? null : full;
        }
        default -> null;
        };
    }

    // =========================================================================
    // Part heading, chapter heading + scenes
    // =========================================================================

    private void appendPartHeading(StringBuilder body, Part part, boolean pageBreakBefore) {
        String num   = toRoman(part.getPartNumber());
        String title = (part.getTitle() != null && !part.getTitle().isBlank())
                ? part.getTitle()
                : "Part " + num;

        String cls = pageBreakBefore ? "part-title part-break" : "part-title";
        body.append("<p class=\"").append(cls).append("\">").append(escapeHtml(title)).append("</p>");

        if (part.getSubtitle() != null && !part.getSubtitle().isBlank()) {
            body.append("<p class=\"part-subtitle\">").append(escapeHtml(part.getSubtitle())).append("</p>");
        }
    }

    private void appendChapterContent(StringBuilder body, Chapter chapter, boolean pageBreakBefore)
            throws Exception {
        String title = (chapter.getTitle() != null && !chapter.getTitle().isBlank())
                ? chapter.getTitle()
                : "Chapter " + chapter.getChapterNumber();

        String cls = pageBreakBefore ? "chapter-title chapter-break" : "chapter-title";
        body.append("<p class=\"").append(cls).append("\">").append(escapeHtml(title)).append("</p>");

        if (chapter.getSubtitle() != null && !chapter.getSubtitle().isBlank()) {
            body.append("<p class=\"chapter-subtitle\">").append(escapeHtml(chapter.getSubtitle())).append("</p>");
        }

        List<Scene> scenes     = sceneDao.findByChapterId(chapter.getId());
        boolean     firstScene = true;
        for (Scene s : scenes) {
            Scene scene = (s.getContent() != null && !s.getContent().isBlank())
                    ? s
                    : sceneDao.findById(s.getId()).orElse(s);

            if (!firstScene)
                body.append("<p class=\"scene-break\">* * *</p>");
            if (scene.getContent() != null && !scene.getContent().isBlank()) {
                body.append(sceneHtmlFragment(scene.getContent()));
            }
            firstScene = false;
        }
    }

    /**
     * Prepares a scene's TipTap HTML for embedding: replaces {@code <hr>}
     * (in-content scene breaks) with the same "* * *" marker used between
     * scene rows, matching {@link ExportService}'s DOCX conversion. All other
     * tags (paragraphs, headings, lists, blockquotes, images, inline marks)
     * pass through unchanged — the renderer's own CSS 2.1 layout handles
     * them, unlike the DOCX path which must reconstruct each one manually.
     */
    private String sceneHtmlFragment(String html) {
        if (html == null || html.isBlank())
            return "";
        org.jsoup.nodes.Document jsoup = Jsoup.parseBodyFragment(html);
        for (Element hr : jsoup.select("hr")) {
            Element marker = jsoup.createElement("p").addClass("scene-break").text("* * *");
            hr.replaceWith(marker);
        }
        return jsoup.body().html();
    }

    // =========================================================================
    // Document assembly + rendering
    // =========================================================================

    /** Wraps assembled body HTML in a full XHTML document with the stylesheet. */
    private String wrapDocument(String bodyHtml, PageLayout layout, String runningHeader) {
        String css = buildCss(layout, runningHeader);
        return "<html><head><meta charset=\"UTF-8\"/><style>" + css + "</style></head>"
                + "<body>" + bodyHtml + "</body></html>";
    }

    /**
     * Builds the stylesheet for one export. Two named pages are defined:
     * {@code cover} (no running header — mirrors the DOCX section break that
     * excludes the cover/title pages from the header) and the default page
     * (running header on every page, used for the rest of the content). Both
     * share the same size/margins resolved from the book's page layout.
     */
    private String buildCss(PageLayout layout, String runningHeader) {
        double wIn     = pageWidthIn(layout);
        double hIn     = pageHeightIn(layout);
        double topIn   = marginTop(layout);
        double botIn   = marginBottom(layout);
        double leftIn  = marginInner(layout);
        double rightIn = marginOuter(layout);

        String headerRule = (runningHeader != null && !runningHeader.isBlank())
                ? "@top-right { content: \"" + cssString(runningHeader) + "\" counter(page); "
                        + "font-family: '" + FONT_FAMILY + "', serif; font-size: 10pt; }"
                : "";

        return """
                @page cover {
                  size: %1$sin %2$sin;
                  margin-top: %3$sin; margin-bottom: %4$sin;
                  margin-left: %5$sin; margin-right: %6$sin;
                }
                @page {
                  size: %1$sin %2$sin;
                  margin-top: %3$sin; margin-bottom: %4$sin;
                  margin-left: %5$sin; margin-right: %6$sin;
                  %7$s
                }
                body {
                  font-family: '%8$s', serif;
                  font-size: 12pt;
                  margin: 0;
                }
                .cover-page { page: cover; }
                .cover-page-break { page-break-before: always; }
                .main { page-break-before: always; }
                .part-break, .chapter-break { page-break-before: always; }
                p {
                  font-family: '%8$s', serif;
                  font-size: 12pt;
                  line-height: 2;
                  text-indent: 0.5in;
                  margin: 0;
                }
                p[data-style="emphasis"] { font-style: italic; }
                p[data-style="report"] {
                  text-indent: 0; line-height: 1; margin-top: 6pt; margin-bottom: 6pt;
                }
                p[data-style="chapter_title"], p[data-style="cover_title"], p[data-style="part_title"],
                p.chapter-title, p.part-title {
                  text-indent: 0; text-align: center; line-height: 1;
                  margin-top: 24pt; margin-bottom: 12pt;
                  font-size: 16pt; font-weight: bold;
                }
                p.part-title { font-size: 20pt; margin-top: 144pt; }
                p[data-style="chapter_subtitle"], p[data-style="cover_subtitle"], p[data-style="part_subtitle"],
                p.chapter-subtitle, p.part-subtitle {
                  text-indent: 0; text-align: center; line-height: 1;
                  margin-top: 0; margin-bottom: 12pt;
                  font-size: 14pt; font-style: italic;
                }
                p.part-subtitle { margin-top: 6pt; }
                p.scene-break {
                  text-indent: 0; text-align: center; line-height: 1;
                  margin-top: 12pt; margin-bottom: 12pt;
                }
                blockquote { margin: 0; }
                blockquote p {
                  text-indent: 0; line-height: 1;
                  margin-top: 6pt; margin-bottom: 6pt;
                  margin-left: 0.5in; margin-right: 0.5in;
                }
                h1, h2, h3 {
                  font-family: '%8$s', serif;
                  font-weight: bold; font-style: normal;
                  text-indent: 0; line-height: 1;
                  margin-top: 12pt; margin-bottom: 6pt;
                }
                h1 { font-size: 16pt; }
                h2 { font-size: 14pt; }
                h3 { font-size: 12pt; }
                ul, ol { margin: 0 0 0 0.5in; padding-left: 0.5in; line-height: 2; }
                li { margin: 0; }
                img {
                  max-width: 100%%; height: auto; display: block;
                  margin: 6pt auto;
                }
                img[data-align="left"] { margin-left: 0; margin-right: auto; }
                img[data-align="right"] { margin-left: auto; margin-right: 0; }
                .cover-page img { max-width: 100%%; }
                """.formatted(trimNum(wIn), trimNum(hIn), trimNum(topIn), trimNum(botIn),
                trimNum(leftIn), trimNum(rightIn), headerRule, FONT_FAMILY);
    }

    /** Renders an XHTML document string to PDF bytes using standard PDF fonts (no font files needed). */
    private byte[] renderPdf(String html) throws Exception {
        org.jsoup.nodes.Document jsoupDoc = Jsoup.parse(html);
        jsoupDoc.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml);
        Document w3cDoc = new W3CDom().fromJsoup(jsoupDoc);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(w3cDoc, "");

            builder.useFont(
                    new PDFontSupplier(new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN)),
                    FONT_FAMILY, 400, FontStyle.NORMAL, false);
            builder.useFont(
                    new PDFontSupplier(new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD)),
                    FONT_FAMILY, 700, FontStyle.NORMAL, false);
            builder.useFont(
                    new PDFontSupplier(new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC)),
                    FONT_FAMILY, 400, FontStyle.ITALIC, false);
            builder.useFont(
                    new PDFontSupplier(new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD_ITALIC)),
                    FONT_FAMILY, 700, FontStyle.ITALIC, false);

            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        }
    }

    // =========================================================================
    // Running header text
    // =========================================================================

    /** Builds "Last Name / Short Title / " (any missing part omitted), same as DOCX. */
    private String runningHeaderText(Book book, Project project) {
        String lastName   = (project != null && project.getAuthorLastName() != null)
                ? project.getAuthorLastName().trim()
                : "";
        String shortTitle = (book.getShortTitle() != null && !book.getShortTitle().isBlank())
                ? book.getShortTitle().trim()
                : (book.getTitle() != null ? book.getTitle().trim() : "");

        StringBuilder prefix = new StringBuilder();
        if (!lastName.isEmpty())
            prefix.append(lastName);
        if (!shortTitle.isEmpty()) {
            if (prefix.length() > 0)
                prefix.append(" / ");
            prefix.append(shortTitle);
        }
        if (prefix.length() > 0)
            prefix.append(" / ");
        return prefix.toString();
    }

    // =========================================================================
    // Page layout helpers (mirrors ExportService's DOCX page-dimension logic)
    // =========================================================================

    private Project loadProject(Book book) {
        if (book.getProjectId() == null)
            return null;
        try {
            return projectDao.findById(book.getProjectId()).orElse(null);
        } catch (Exception e) {
            logger.warn("Could not load project for PDF export header: {}", e.getMessage());
            return null;
        }
    }

    private double pageWidthIn(PageLayout b) {
        if (!b.isPageLayoutEnabled())
            return DEFAULT_WIDTH_IN;
        if ("CUSTOM".equals(b.getPageSizePreset()) && b.getPageWidthIn() != null)
            return b.getPageWidthIn();
        return switch (b.getPageSizePreset() != null ? b.getPageSizePreset() : "LETTER") {
        case "A4" -> 8.27;
        case "TRADE_PAPERBACK" -> 6.0;
        case "MASS_MARKET" -> 4.25;
        case "HARDBACK" -> 6.14;
        default -> DEFAULT_WIDTH_IN;
        };
    }

    private double pageHeightIn(PageLayout b) {
        if (!b.isPageLayoutEnabled())
            return DEFAULT_HEIGHT_IN;
        if ("CUSTOM".equals(b.getPageSizePreset()) && b.getPageHeightIn() != null)
            return b.getPageHeightIn();
        return switch (b.getPageSizePreset() != null ? b.getPageSizePreset() : "LETTER") {
        case "A4" -> 11.69;
        case "TRADE_PAPERBACK" -> 9.0;
        case "MASS_MARKET" -> 6.87;
        case "HARDBACK" -> 9.21;
        default -> DEFAULT_HEIGHT_IN;
        };
    }

    private double marginTop(PageLayout b) {
        return (!b.isPageLayoutEnabled() || b.getPageMarginTopIn() == null) ? DEFAULT_TOP_IN : b.getPageMarginTopIn();
    }

    private double marginBottom(PageLayout b) {
        return (!b.isPageLayoutEnabled() || b.getPageMarginBottomIn() == null) ? DEFAULT_BOTTOM_IN
                : b.getPageMarginBottomIn();
    }

    private double marginInner(PageLayout b) {
        return (!b.isPageLayoutEnabled() || b.getPageMarginInnerIn() == null) ? DEFAULT_INNER_IN
                : b.getPageMarginInnerIn();
    }

    private double marginOuter(PageLayout b) {
        return (!b.isPageLayoutEnabled() || b.getPageMarginOuterIn() == null) ? DEFAULT_OUTER_IN
                : b.getPageMarginOuterIn();
    }

    // =========================================================================
    // Small string / formatting helpers
    // =========================================================================

    /** Trims trailing ".0" so "8.5in" doesn't render as "8.500000in". */
    private String trimNum(double v) {
        String s = String.valueOf(Math.round(v * 1000.0) / 1000.0);
        if (s.endsWith(".0"))
            s = s.substring(0, s.length() - 2);
        return s;
    }

    /** Escapes a value for embedding inside a CSS string literal (double-quoted). */
    private String cssString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Escapes a value for embedding as HTML text content (title/subtitle strings we build ourselves). */
    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String toRoman(int n) {
        if (n <= 0)
            return String.valueOf(n);
        int[]         vals = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
        String[]      syms = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
        StringBuilder sb   = new StringBuilder();
        for (int i = 0; i < vals.length; i++)
            while (n >= vals[i]) {
                sb.append(syms[i]);
                n -= vals[i];
            }
        return sb.toString();
    }

    /** Current local time formatted as {@code yyyyMMdd-HHmmss}. */
    private String fileTimestamp() {
        return DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
    }

    /**
     * Builds the book-slug portion of the export filename. Prefers
     * {@code shortTitle} when set; falls back to {@code title}.
     */
    private String bookSlug(Book book) {
        String base = (book.getShortTitle() != null && !book.getShortTitle().isBlank())
                ? book.getShortTitle()
                : book.getTitle();
        if (base == null || base.isBlank())
            return "export";
        return base.replace(' ', '_').replaceAll("[\\\\/:*?\"<>|]", "");
    }

    /** Builds a full {@code .pdf} filename: {@code {bookSlug}[-qualifier]-{timestamp}.pdf}. */
    private String pdfFilename(Book book, String qualifier) {
        StringBuilder sb = new StringBuilder(bookSlug(book));
        if (qualifier != null && !qualifier.isBlank()) {
            sb.append('-').append(
                    qualifier.replace(' ', '_').replaceAll("[\\\\/:*?\"<>|]", ""));
        }
        sb.append('-').append(fileTimestamp()).append(".pdf");
        return sb.toString();
    }

    // =========================================================================
    // DAO wrappers with clean error messages
    // =========================================================================

    private Book requireBook(UUID id) throws Exception {
        return bookDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + id));
    }

    private Part requirePart(UUID id) throws Exception {
        return partDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Part not found: " + id));
    }

    private Chapter requireChapter(UUID id) throws Exception {
        return chapterDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + id));
    }

    private Scene requireScene(UUID id) throws Exception {
        return sceneDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + id));
    }
}
