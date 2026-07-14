package com.richardsand.novelkms.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDocument1;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Converts NovelKMS manuscript content (stored as TipTap HTML) into .docx
 * files using Apache POI XWPF.
 *
 * <p>
 * Supports four export scopes: book, part, chapter, scene.
 *
 * <p>
 * Body paragraphs are rendered double-spaced with a 0.5-inch first-line
 * indent (standard manuscript format). Inline bold, italic, underline, and
 * strikethrough are preserved. Embedded base64 images are re-embedded as
 * OOXML pictures. Page size and margins are taken from the book's page-layout
 * settings when enabled; otherwise Letter / 1-inch margins are used.
 */
public class ExportService {

    private static final Logger logger = LoggerFactory.getLogger(ExportService.class);

    // Paragraph defaults
    private static final String DEFAULT_FONT      = "Times New Roman";
    private static final int    DEFAULT_SIZE_PT   = 12;
    /** Double spacing: 480 in OpenXML "auto" line-height units (240 = 1 line). */
    private static final int    DOUBLE_SPACING    = 480;
    private static final int    SINGLE_SPACING    = 240;
    /** 0.5-inch first-line indent in twips (1 twip = 1/1440 inch). */
    private static final int    FIRST_LINE_INDENT = 720;
    /** 0.5-inch blockquote side indent in twips. */
    private static final int    BLOCKQUOTE_INDENT = 720;

    // Heading font sizes
    private static final int H1_SIZE_PT = 16;
    private static final int H2_SIZE_PT = 14;
    private static final int H3_SIZE_PT = 12;

    // Default page dimensions when page layout is disabled (Letter, 1" margins)
    private static final double DEFAULT_WIDTH_IN  = 8.5;
    private static final double DEFAULT_HEIGHT_IN = 11.0;
    private static final double DEFAULT_TOP_IN    = 1.0;
    private static final double DEFAULT_BOTTOM_IN = 1.0;
    private static final double DEFAULT_INNER_IN  = 1.25;
    private static final double DEFAULT_OUTER_IN  = 1.0;

    private final BookDao     bookDao;
    private final PartDao     partDao;
    private final ChapterDao  chapterDao;
    private final SceneDao    sceneDao;
    private final ProjectDao  projectDao;
    private final TemplateDao templateDao;
    private final PageLayoutDao pageLayoutDao;

    public ExportService(BookDao bookDao, PartDao partDao, ChapterDao chapterDao,
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

    /** Byte content and suggested filename for the downloaded docx. */
    public record ExportMeta(byte[] bytes, String filename) {
    }

    // =========================================================================
    // Entry points
    // =========================================================================

    /**
     * Exports the entire book: optional cover image page, then parts/chapters
     * in display order, each chapter on its own page.
     */
    public ExportMeta exportBook(UUID bookId) throws Exception {
        logger.info("Starting DOCX book export: bookId={}", bookId);
        Book       book     = requireBook(bookId);
        Project    project  = loadProject(book);
        PageLayout layout   = pageLayoutDao.resolveBook(book.getId());
        double     contentW = contentWidthIn(layout);

        XWPFDocument doc      = createDocument(layout);
        boolean      pbNeeded = false;

        // Cover image page (if present)
        if (book.isHasCoverImage()) {
            appendCoverImage(doc, book, contentW);
            pbNeeded = true;
        }

        // Cover template (title page) — always rendered immediately after cover image.
        appendCoverTemplate(doc, book, contentW, pbNeeded);

        // Section break ends the "cover" section (no header reference → no header on
        // cover image page or title page). The default "nextPage" section type means
        // the first main-content paragraph begins at the top of a fresh page without
        // needing an additional pageBreakBefore on the heading.
        addCoverSectionBreak(doc, layout);
        pbNeeded = false;

        // Walk the book outline: parts and direct-book chapters interleaved in one
        // display_order sequence (V40), each part immediately followed by its own
        // chapters. Exporting the parts first and the direct chapters afterwards —
        // as this did before V40 — would print a prologue at the end of the book.
        for (Object node : bookOutline(bookId)) {
            if (node instanceof Part part) {
                appendPartHeading(doc, part, pbNeeded);
                pbNeeded = true;
                for (Chapter ch : chapterDao.findByPartId(part.getId())) {
                    appendChapterContent(doc, ch, true, contentW);
                }
            } else {
                appendChapterContent(doc, (Chapter) node, pbNeeded, contentW);
                pbNeeded = true;
            }
        }

        // Running header is attached to the document-level sectPr (= section 2).
        // It therefore appears on every page of the main content but NOT on the
        // cover pages (which belong to the section that ended at addCoverSectionBreak).
        addRunningHeader(doc, book, project);

        String filename = docxFilename(book, null);
        return new ExportMeta(toBytes(doc), filename);
    }


    /**
     * The book's top-level nodes in linear order: {@link Part}s and direct-book
     * {@link Chapter}s merged on the shared outline {@code display_order}
     * sequence introduced in V40.
     *
     * <p>Both lists already come back sorted, so this is a straight merge — no
     * extra query. Every export surface (DOCX, PDF, ePub) needs exactly this
     * walk, but the three services share no base class, so each keeps its own
     * copy in the same style as the existing scene-break duplication.
     */
    private List<Object> bookOutline(UUID bookId) throws Exception {
        List<Part>    parts          = partDao.findByBookId(bookId);
        List<Chapter> directChapters = chapterDao.findByBookId(bookId);

        List<Object> nodes = new ArrayList<>(parts.size() + directChapters.size());
        int p = 0;
        int c = 0;
        while (p < parts.size() || c < directChapters.size()) {
            boolean takePart;
            if (p >= parts.size()) {
                takePart = false;
            } else if (c >= directChapters.size()) {
                takePart = true;
            } else {
                takePart = parts.get(p).getDisplayOrder() <= directChapters.get(c).getDisplayOrder();
            }
            nodes.add(takePart ? parts.get(p++) : directChapters.get(c++));
        }
        return nodes;
    }

    /**
     * Exports a single part: part heading, then its chapters in order.
     */
    public ExportMeta exportPart(UUID partId) throws Exception {
        Part       part     = requirePart(partId);
        Book       book     = requireBook(part.getBookId());
        Project    project  = loadProject(book);
        PageLayout layout   = pageLayoutDao.resolveBook(book.getId());
        double     contentW = contentWidthIn(layout);

        XWPFDocument doc = createDocument(layout);
        appendPartHeading(doc, part, false);

        for (Chapter ch : chapterDao.findByPartId(partId)) {
            appendChapterContent(doc, ch, true, contentW);
        }

        addRunningHeader(doc, book, project);

        String partLabel = (part.getTitle() != null && !part.getTitle().isBlank())
                ? part.getTitle()
                : "Part " + toRoman(part.getPartNumber());
        String filename  = docxFilename(book, partLabel);
        return new ExportMeta(toBytes(doc), filename);
    }

    /**
     * Exports a single chapter: chapter heading then all scenes.
     */
    public ExportMeta exportChapter(UUID chapterId) throws Exception {
        Chapter    chapter  = requireChapter(chapterId);
        Book       book     = requireBook(chapter.getBookId());
        Project    project  = loadProject(book);
        PageLayout layout   = pageLayoutDao.resolveBook(book.getId());
        double     contentW = contentWidthIn(layout);

        XWPFDocument doc = createDocument(layout);
        appendChapterContent(doc, chapter, false, contentW);

        addRunningHeader(doc, book, project);

        String chLabel  = (chapter.getTitle() != null && !chapter.getTitle().isBlank())
                ? chapter.getTitle()
                : "Chapter " + chapter.getChapterNumber();
        String filename = docxFilename(book, chLabel);
        return new ExportMeta(toBytes(doc), filename);
    }

    /**
     * Exports a single scene: raw content only, no heading.
     */
    public ExportMeta exportScene(UUID sceneId) throws Exception {
        Scene      scene   = requireScene(sceneId);
        Chapter    chapter = requireChapter(scene.getChapterId());
        Book       book    = requireBook(chapter.getBookId());
        Project    project = loadProject(book);
        PageLayout layout  = pageLayoutDao.resolveBook(book.getId());

        XWPFDocument doc = createDocument(layout);
        convertHtml(doc, scene.getContent(), contentWidthIn(layout));

        addRunningHeader(doc, book, project);

        String sceneLabel = (scene.getTitle() != null && !scene.getTitle().isBlank())
                ? scene.getTitle()
                : null;
        String filename   = docxFilename(book, sceneLabel);
        return new ExportMeta(toBytes(doc), filename);
    }

    // =========================================================================
    // Running header + cover section break
    // =========================================================================

    /**
     * Loads the project that owns this book, returning null on failure.
     * Used by header generation; the header degrades gracefully when project
     * data is unavailable.
     */
    private Project loadProject(Book book) {
        if (book.getProjectId() == null)
            return null;
        try {
            return projectDao.findById(book.getProjectId()).orElse(null);
        } catch (Exception e) {
            logger.warn("Could not load project for export header: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Attaches a manuscript running header to the document-level sectPr.
     *
     * <p>
     * Format: {@code Last Name / Short Title / page-number} right-justified.
     * The page number is a live {@code PAGE} field so Word updates it on each
     * page. Any component that is null or blank is omitted gracefully.
     *
     * <p>
     * The header is referenced from the document's <em>last</em> sectPr
     * (the document-level one), so it applies to every page in the final
     * section of the document. When {@link #addCoverSectionBreak} has been
     * called first, the cover pages live in a prior section that has no header
     * reference, giving exactly the "skip cover pages" behaviour required.
     */
    private void addRunningHeader(XWPFDocument doc, Book book, Project project) {
        String lastName   = (project != null && project.getAuthorLastName() != null)
                ? project.getAuthorLastName().trim()
                : "";
        String shortTitle = (book.getShortTitle() != null && !book.getShortTitle().isBlank())
                ? book.getShortTitle().trim()
                : (book.getTitle() != null ? book.getTitle().trim() : "");

        // Build the static prefix — everything before the page-number field.
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

        XWPFHeader    header = doc.createHeader(HeaderFooterType.DEFAULT);
        XWPFParagraph para   = header.getParagraphs().isEmpty()
                ? header.createParagraph()
                : header.getParagraphs().get(0);
        para.setAlignment(ParagraphAlignment.RIGHT);

        // Static text prefix ("Last Name / Short Title / ")
        if (prefix.length() > 0) {
            XWPFRun r = para.createRun();
            r.setFontFamily(DEFAULT_FONT);
            r.setFontSize(DEFAULT_SIZE_PT);
            r.setText(prefix.toString());
        }

        // Live PAGE field: <w:fldChar type="begin"/> <w:instrText> PAGE </w:instrText>
        // <w:fldChar type="end"/>
        XWPFRun r1 = para.createRun();
        r1.getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);

        XWPFRun r2 = para.createRun();
        r2.getCTR().addNewInstrText().setStringValue(" PAGE \\* MERGEFORMAT ");

        XWPFRun r3 = para.createRun();
        r3.getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
    }

    /**
     * Inserts the section-break paragraph that ends the "cover" section.
     *
     * <p>
     * In OOXML, a section break is represented by embedding a
     * {@code <w:sectPr>} inside a paragraph's {@code <w:pPr>}. The embedded
     * sectPr carries no {@code <w:headerReference>} element, so all pages in
     * this section (cover image + title page) render with no header.
     *
     * <p>
     * The section type defaults to {@code nextPage} in Word when no explicit
     * type is set, which means the first paragraph of the following section
     * (the first part or chapter heading) begins on a fresh page automatically —
     * no separate {@code pageBreakBefore} attribute is needed on that heading.
     *
     * <p>
     * The embedded sectPr also needs its own {@code pgSz}/{@code pgMar} set
     * explicitly. Word does not have the cover section inherit dimensions
     * from the document-level sectPr the main content uses — an unset
     * {@code pgSz} here would silently fall back to Word's application
     * default (Letter), so a custom page size would apply to the manuscript
     * but not to the cover image/title pages. {@code layout} is the same
     * resolved value {@link #applyPageLayout} used for the main section.
     */
    private void addCoverSectionBreak(XWPFDocument doc, PageLayout layout) {
        XWPFParagraph para = doc.createParagraph();
        CTPPr         pPr  = ppr(para);
        // Minimise the visual footprint of this paragraph on the title page.
        CTSpacing sp = spacing(pPr);
        sp.setBefore(BigInteger.ZERO);
        sp.setAfter(BigInteger.ZERO);
        // The embedded sectPr with no headerReference = no header in the cover section.
        CTSectPr coverSectPr = pPr.addNewSectPr();
        applySectPrPageLayout(coverSectPr, layout);
    }

    // =========================================================================
    // Document and page layout
    // =========================================================================

    private XWPFDocument createDocument(PageLayout layout) {
        XWPFDocument doc = new XWPFDocument();
        applyPageLayout(doc, layout);
        return doc;
    }

    private void applyPageLayout(XWPFDocument doc, PageLayout book) {
        CTDocument1 ctDoc  = doc.getDocument();
        CTBody      body   = ctDoc.getBody();
        CTSectPr    sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        applySectPrPageLayout(sectPr, book);
    }

    /**
     * Sets page size and margins on a given {@code sectPr}. Shared by the
     * document-level section (main manuscript content) and the embedded
     * cover-section {@code sectPr} ({@link #addCoverSectionBreak}), so both
     * sections always agree on dimensions.
     */
    private void applySectPrPageLayout(CTSectPr sectPr, PageLayout book) {
        double wIn     = pageWidthIn(book);
        double hIn     = pageHeightIn(book);
        double topIn   = marginTop(book);
        double botIn   = marginBottom(book);
        double leftIn  = marginInner(book);
        double rightIn = marginOuter(book);

        CTPageSz pgSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pgSz.setW(BigInteger.valueOf(Math.round(wIn * 1440)));
        pgSz.setH(BigInteger.valueOf(Math.round(hIn * 1440)));

        CTPageMar pgMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pgMar.setTop(BigInteger.valueOf(Math.round(topIn * 1440)));
        pgMar.setBottom(BigInteger.valueOf(Math.round(botIn * 1440)));
        pgMar.setLeft(BigInteger.valueOf(Math.round(leftIn * 1440)));
        pgMar.setRight(BigInteger.valueOf(Math.round(rightIn * 1440)));
    }

    // Page dimension helpers (fall back to Letter/1" when layout is disabled)
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
        return (!b.isPageLayoutEnabled() || b.getPageMarginBottomIn() == null) ? DEFAULT_BOTTOM_IN : b.getPageMarginBottomIn();
    }

    private double marginInner(PageLayout b) {
        return (!b.isPageLayoutEnabled() || b.getPageMarginInnerIn() == null) ? DEFAULT_INNER_IN : b.getPageMarginInnerIn();
    }

    private double marginOuter(PageLayout b) {
        return (!b.isPageLayoutEnabled() || b.getPageMarginOuterIn() == null) ? DEFAULT_OUTER_IN : b.getPageMarginOuterIn();
    }

    /** Usable content width for images. */
    private double contentWidthIn(PageLayout b) {
        return pageWidthIn(b) - marginInner(b) - marginOuter(b);
    }

    // =========================================================================
    // Cover image
    // =========================================================================

    private void appendCoverImage(XWPFDocument doc, Book book, double contentW) {
        BookDao.CoverImage img;
        try {
            img = bookDao.getCoverImage(book.getId()).orElse(null);
        } catch (Exception e) {
            logger.warn("Could not load cover image for export: {}", e.getMessage());
            return;
        }
        if (img == null)
            return;

        double   maxW = contentW;
        double[] dims = imageDimensions(img.data(), maxW);

        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(ParagraphAlignment.CENTER);
        CTPPr pPr = ppr(para);
        spacing(pPr).setBefore(BigInteger.ZERO);
        spacing(pPr).setAfter(BigInteger.ZERO);
        ind(pPr).setFirstLine(BigInteger.ZERO);

        XWPFRun run = para.createRun();
        try (ByteArrayInputStream is = new ByteArrayInputStream(img.data())) {
            run.addPicture(is, mimeToPoiType(img.mimeType()), "cover",
                    (int) Units.toEMU(dims[0] * 72),
                    (int) Units.toEMU(dims[1] * 72));
        } catch (Exception e) {
            logger.warn("Failed to embed cover image in DOCX: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Cover template (title page)
    // =========================================================================

    /**
     * Renders the resolved COVER template (BOOK override if present, otherwise
     * GLOBAL default) as DOCX paragraphs.
     *
     * <p>
     * Token spans ({@code <span data-token="TITLE">}) are replaced with live
     * values from the book and its project before conversion. If
     * {@code pageBreakBefore} is true, {@code <w:pageBreakBefore/>} is pinned to
     * the first paragraph produced, starting the title page on a new page without
     * creating a blank intermediary paragraph.
     */
    private void appendCoverTemplate(XWPFDocument doc, Book book,
            double contentW, boolean pageBreakBefore) {
        try {
            Template template = templateDao.resolveForBook(book.getId(), TemplateDao.TYPE_COVER);
            String   html     = template.getContent();
            if (html == null || html.isBlank())
                return;

            String resolved = resolveTokens(html, book);

            // Track which paragraph is "first" so we can attach pageBreakBefore to it
            // rather than emitting a separate (potentially blank) break paragraph.
            int firstIdx = doc.getParagraphs().size();
            convertHtml(doc, resolved, contentW);

            if (pageBreakBefore && doc.getParagraphs().size() > firstIdx) {
                ppr(doc.getParagraphs().get(firstIdx)).addNewPageBreakBefore();
            }
        } catch (Exception e) {
            logger.warn("Could not render cover template in DOCX export: {}", e.getMessage());
        }
    }

    /**
     * Resolves {@code <span data-token="...">} placeholders in template HTML
     * by replacing their text with live values from the book and its project.
     * Tokens with no matching value are replaced with an empty string so the
     * surrounding markup (e.g. "By ") is preserved exactly as the author set it.
     */
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
    // Part heading
    // =========================================================================

    private void appendPartHeading(XWPFDocument doc, Part part, boolean pageBreakBefore) {
        String num   = toRoman(part.getPartNumber());
        String title = (part.getTitle() != null && !part.getTitle().isBlank())
                ? part.getTitle()
                : "Part " + num;

        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(ParagraphAlignment.CENTER);
        CTPPr pPr = ppr(para);
        if (pageBreakBefore)
            pPr.addNewPageBreakBefore();
        spacing(pPr).setBefore(BigInteger.valueOf(2880)); // 2 inches before (top-of-page feel)
        spacing(pPr).setAfter(BigInteger.valueOf(480));
        spacing(pPr).setLine(BigInteger.valueOf(SINGLE_SPACING));
        spacing(pPr).setLineRule(STLineSpacingRule.AUTO);
        ind(pPr).setFirstLine(BigInteger.ZERO);
        headingRun(para, title, 20, true, false);

        if (part.getSubtitle() != null && !part.getSubtitle().isBlank()) {
            XWPFParagraph sub = doc.createParagraph();
            sub.setAlignment(ParagraphAlignment.CENTER);
            CTPPr sPPr = ppr(sub);
            spacing(sPPr).setBefore(BigInteger.valueOf(120));
            spacing(sPPr).setAfter(BigInteger.ZERO);
            spacing(sPPr).setLine(BigInteger.valueOf(SINGLE_SPACING));
            spacing(sPPr).setLineRule(STLineSpacingRule.AUTO);
            ind(sPPr).setFirstLine(BigInteger.ZERO);
            headingRun(sub, part.getSubtitle(), H2_SIZE_PT, false, true);
        }
    }

    // =========================================================================
    // Chapter heading + scenes
    // =========================================================================

    private void appendChapterContent(XWPFDocument doc, Chapter chapter,
            boolean pageBreakBefore, double contentW) throws Exception {
        // Chapter heading
        String title = (chapter.getTitle() != null && !chapter.getTitle().isBlank())
                ? chapter.getTitle()
                : "Chapter " + chapter.getChapterNumber();

        XWPFParagraph titlePara = doc.createParagraph();
        titlePara.setAlignment(ParagraphAlignment.CENTER); // fix: was left-aligned
        CTPPr tPPr = ppr(titlePara);
        if (pageBreakBefore)
            tPPr.addNewPageBreakBefore();
        spacing(tPPr).setBefore(BigInteger.valueOf(480));
        spacing(tPPr).setAfter(BigInteger.valueOf(240));
        spacing(tPPr).setLine(BigInteger.valueOf(SINGLE_SPACING));
        spacing(tPPr).setLineRule(STLineSpacingRule.AUTO);
        ind(tPPr).setFirstLine(BigInteger.ZERO);
        headingRun(titlePara, title, H1_SIZE_PT, true, false);

        if (chapter.getSubtitle() != null && !chapter.getSubtitle().isBlank()) {
            XWPFParagraph subPara = doc.createParagraph();
            subPara.setAlignment(ParagraphAlignment.CENTER);
            CTPPr sPPr = ppr(subPara);
            spacing(sPPr).setBefore(BigInteger.ZERO);
            spacing(sPPr).setAfter(BigInteger.valueOf(240));
            spacing(sPPr).setLine(BigInteger.valueOf(SINGLE_SPACING));
            spacing(sPPr).setLineRule(STLineSpacingRule.AUTO);
            ind(sPPr).setFirstLine(BigInteger.ZERO);
            headingRun(subPara, chapter.getSubtitle(), H2_SIZE_PT, false, true);
        }

        // Scenes separated by scene-break paragraphs
        List<Scene> scenes     = sceneDao.findByChapterId(chapter.getId());
        boolean     firstScene = true;
        for (Scene s : scenes) {
            // Ensure content is loaded (findByChapterId may return metadata-only rows)
            Scene scene = (s.getContent() != null && !s.getContent().isBlank())
                    ? s
                    : sceneDao.findById(s.getId()).orElse(s);

            if (!firstScene)
                appendSceneBreak(doc);
            if (scene.getContent() != null && !scene.getContent().isBlank()) {
                convertHtml(doc, scene.getContent(), contentW);
            }
            firstScene = false;
        }
    }

    private void appendSceneBreak(XWPFDocument doc) {
        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(ParagraphAlignment.CENTER);
        CTPPr pPr = ppr(para);
        spacing(pPr).setBefore(BigInteger.valueOf(SINGLE_SPACING));
        spacing(pPr).setAfter(BigInteger.valueOf(SINGLE_SPACING));
        spacing(pPr).setLine(BigInteger.valueOf(SINGLE_SPACING));
        spacing(pPr).setLineRule(STLineSpacingRule.AUTO);
        ind(pPr).setFirstLine(BigInteger.ZERO);
        XWPFRun run = para.createRun();
        run.setFontFamily(DEFAULT_FONT);
        run.setFontSize(DEFAULT_SIZE_PT);
        run.setText("* * *");
    }

    // =========================================================================
    // HTML → DOCX converter
    // =========================================================================

    /**
     * Parses TipTap-generated HTML and appends paragraphs to the document.
     *
     * @param doc             target document
     * @param html            scene content HTML
     * @param maxImageWidthIn maximum image width in inches (content width)
     */
    private void convertHtml(XWPFDocument doc, String html, double maxImageWidthIn) {
        if (html == null || html.isBlank())
            return;
        org.jsoup.nodes.Document jsoup = Jsoup.parseBodyFragment(html);
        for (Element el : jsoup.body().children()) {
            convertTopLevel(doc, el, maxImageWidthIn);
        }
    }

    private void convertTopLevel(XWPFDocument doc, Element el, double maxW) {
        switch (el.tagName().toLowerCase()) {
        case "p" -> convertP(doc, el, maxW);
        case "h1" -> convertHn(doc, el, H1_SIZE_PT);
        case "h2" -> convertHn(doc, el, H2_SIZE_PT);
        case "h3" -> convertHn(doc, el, H3_SIZE_PT);
        case "hr" -> appendSceneBreak(doc);
        case "ul" -> convertList(doc, el, false);
        case "ol" -> convertList(doc, el, true);
        case "blockquote" -> convertBlockquote(doc, el);
        case "img" -> convertImage(doc, el, maxW);
        default -> {
        }
        }
    }

    // ── Paragraph ─────────────────────────────────────────────────────────────

    private void convertP(XWPFDocument doc, Element el, double maxW) {
        // Paragraph is image-only → render as image paragraph(s)
        Elements imgs = el.select("> img");
        if (!el.hasText() && !imgs.isEmpty()) {
            imgs.forEach(img -> convertImage(doc, img, maxW));
            return;
        }

        String  styleKey = el.attr("data-style");
        boolean emphasis = "emphasis".equals(styleKey);

        XWPFParagraph para = doc.createParagraph();
        applyParaStyle(para, styleKey);
        // Inline style="text-align:..." (e.g. cover template paragraphs) wins over
        // the default left alignment produced by applyParaStyle.
        ParagraphAlignment inlineAlign = styleAlignment(el);
        if (inlineAlign != null) {
            para.setAlignment(inlineAlign);
            // CENTER and RIGHT paragraphs must not carry a first-line indent.
            // In Word, first-line indent shifts the optical centre point of a
            // centred paragraph to the right — the "spurious tab" effect.
            if (inlineAlign == ParagraphAlignment.CENTER
                    || inlineAlign == ParagraphAlignment.RIGHT) {
                ind(ppr(para)).setFirstLine(BigInteger.ZERO);
            }
        }
        convertInline(doc, para, el, false, emphasis, false, false, maxW);
    }

    // ── In-content headings (h1/h2/h3 inside scene HTML) ─────────────────────

    private void convertHn(XWPFDocument doc, Element el, int fontSizePt) {
        XWPFParagraph      para        = doc.createParagraph();
        ParagraphAlignment inlineAlign = styleAlignment(el);
        if (inlineAlign != null)
            para.setAlignment(inlineAlign);
        CTPPr pPr = ppr(para);
        spacing(pPr).setBefore(BigInteger.valueOf(240));
        spacing(pPr).setAfter(BigInteger.valueOf(120));
        spacing(pPr).setLine(BigInteger.valueOf(SINGLE_SPACING));
        spacing(pPr).setLineRule(STLineSpacingRule.AUTO);
        ind(pPr).setFirstLine(BigInteger.ZERO);
        convertInline(doc, para, el, true, false, false, false, 0);
        // Override font size on all runs produced by convertInline
        for (XWPFRun run : para.getRuns()) {
            run.setFontFamily(DEFAULT_FONT);
            run.setFontSize(fontSizePt);
        }
    }

    // ── Blockquote ────────────────────────────────────────────────────────────

    private void convertBlockquote(XWPFDocument doc, Element el) {
        Elements children = el.children();
        if (children.isEmpty()) {
            XWPFParagraph para = doc.createParagraph();
            applyBlockquoteStyle(para);
            convertInline(doc, para, el, false, false, false, false, 0);
        } else {
            for (Element child : children) {
                if ("p".equals(child.tagName())) {
                    XWPFParagraph para = doc.createParagraph();
                    applyBlockquoteStyle(para);
                    convertInline(doc, para, child, false, false, false, false, 0);
                } else {
                    convertTopLevel(doc, child, 0);
                }
            }
        }
    }

    // ── List ──────────────────────────────────────────────────────────────────

    private void convertList(XWPFDocument doc, Element el, boolean ordered) {
        int num = 1;
        for (Element li : el.select("> li")) {
            XWPFParagraph para = doc.createParagraph();
            CTPPr         pPr  = ppr(para);
            ind(pPr).setLeft(BigInteger.valueOf(720));
            ind(pPr).setFirstLine(BigInteger.ZERO);
            CTSpacing sp = spacing(pPr);
            sp.setBefore(BigInteger.ZERO);
            sp.setAfter(BigInteger.ZERO);
            sp.setLine(BigInteger.valueOf(DOUBLE_SPACING));
            sp.setLineRule(STLineSpacingRule.AUTO);

            XWPFRun prefix = para.createRun();
            prefix.setFontFamily(DEFAULT_FONT);
            prefix.setFontSize(DEFAULT_SIZE_PT);
            prefix.setText(ordered ? (num++ + ". ") : "\u2022 ");

            convertInline(doc, para, li, false, false, false, false, 0);
        }
    }

    // ── Image ─────────────────────────────────────────────────────────────────

    private void convertImage(XWPFDocument doc, Element img, double maxW) {
        String src = img.attr("src");
        if (!src.startsWith("data:"))
            return;

        try {
            int commaIdx = src.indexOf(',');
            if (commaIdx < 0)
                return;
            String meta  = src.substring(5, commaIdx);                                      // "image/jpeg;base64"
            String b64   = src.substring(commaIdx + 1);
            String mime  = meta.contains(";") ? meta.substring(0, meta.indexOf(';')) : meta;
            byte[] bytes = Base64.getDecoder().decode(b64);

            // Try to parse stored width attribute for proportional scaling
            double targetW = maxW > 0 ? maxW : DEFAULT_WIDTH_IN - DEFAULT_INNER_IN - DEFAULT_OUTER_IN;
            String wAttr   = img.attr("width");
            if (!wAttr.isBlank()) {
                try {
                    double wPx = Double.parseDouble(wAttr);
                    double wIn = wPx / 96.0;               // assume 96 dpi
                    if (wIn < targetW)
                        targetW = wIn;
                } catch (NumberFormatException ignored) {
                }
            }

            double[] dims = imageDimensions(bytes, targetW);

            XWPFParagraph para      = doc.createParagraph();
            String        alignAttr = img.attr("data-align");
            para.setAlignment("left".equals(alignAttr) ? ParagraphAlignment.LEFT
                    : "right".equals(alignAttr) ? ParagraphAlignment.RIGHT
                            : ParagraphAlignment.CENTER);
            CTPPr     pPr = ppr(para);
            CTSpacing sp  = spacing(pPr);
            sp.setBefore(BigInteger.valueOf(120));
            sp.setAfter(BigInteger.valueOf(120));
            ind(pPr).setFirstLine(BigInteger.ZERO);

            XWPFRun run = para.createRun();
            try (ByteArrayInputStream is = new ByteArrayInputStream(bytes)) {
                run.addPicture(is, mimeToPoiType(mime), "img",
                        (int) Units.toEMU(dims[0] * 72),
                        (int) Units.toEMU(dims[1] * 72));
            }
        } catch (Exception e) {
            logger.warn("Skipping image in DOCX export: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Inline content (recursive)
    // =========================================================================

    /**
     * Walks child nodes of {@code el}, emitting {@link XWPFRun} objects into
     * {@code para}. Inline tags accumulate formatting flags; {@code <br>
     * } emits
     * a line break. Inline {@code <img>} creates a new image paragraph
     * appended after the current one.
     */
    private void convertInline(XWPFDocument doc, XWPFParagraph para, Element el,
            boolean bold, boolean italic, boolean underline, boolean strike,
            double maxW) {

        for (Node node : el.childNodes()) {
            if (node instanceof TextNode tn) {
                String text = tn.text();
                if (!text.isEmpty()) {
                    XWPFRun run = para.createRun();
                    run.setFontFamily(DEFAULT_FONT);
                    run.setFontSize(DEFAULT_SIZE_PT);
                    run.setBold(bold);
                    run.setItalic(italic);
                    if (underline)
                        run.setUnderline(UnderlinePatterns.SINGLE);
                    run.setStrikeThrough(strike);
                    run.setText(text);
                }
            } else if (node instanceof Element child) {
                String tag = child.tagName().toLowerCase();
                switch (tag) {
                case "strong", "b" ->
                    convertInline(doc, para, child, true, italic, underline, strike, maxW);
                case "em", "i" ->
                    convertInline(doc, para, child, bold, true, underline, strike, maxW);
                case "u" ->
                    convertInline(doc, para, child, bold, italic, true, strike, maxW);
                case "s", "del", "strike" ->
                    convertInline(doc, para, child, bold, italic, underline, true, maxW);
                case "br" -> {
                    XWPFRun run = para.createRun();
                    run.addBreak();
                }
                case "img" ->
                    // Image inside inline flow → emit as a separate paragraph
                    convertImage(doc, child, maxW);
                case "span" ->
                    // span[data-token] = empty template token → skip (no text to render)
                    // span[style*=font-size] = font size override → recurse, size not mapped
                    convertInline(doc, para, child, bold, italic, underline, strike, maxW);
                default ->
                    convertInline(doc, para, child, bold, italic, underline, strike, maxW);
                }
            }
        }
    }

    // =========================================================================
    // Paragraph style application
    // =========================================================================

    /**
     * Applies paragraph-level formatting to {@code para} based on the TipTap
     * {@code data-style} attribute value. Body text uses double spacing with
     * a 0.5-inch first-line indent (manuscript standard).
     */
    private void applyParaStyle(XWPFParagraph para, String styleKey) {
        if ("blockquote".equals(styleKey)) {
            applyBlockquoteStyle(para);
            return;
        }

        CTPPr     pPr = ppr(para);
        CTSpacing sp  = spacing(pPr);
        CTInd     id  = ind(pPr);

        // Manuscript default: double-spaced, no extra space between paragraphs,
        // 0.5-inch first-line indent.
        sp.setLine(BigInteger.valueOf(DOUBLE_SPACING));
        sp.setLineRule(STLineSpacingRule.AUTO);
        sp.setBefore(BigInteger.ZERO);
        sp.setAfter(BigInteger.ZERO);
        id.setFirstLine(BigInteger.valueOf(FIRST_LINE_INDENT));

        if (styleKey == null || styleKey.isBlank())
            return;

        switch (styleKey) {
        case "report" -> {
            id.setFirstLine(BigInteger.ZERO);
            sp.setBefore(BigInteger.valueOf(120));
            sp.setAfter(BigInteger.valueOf(120));
            sp.setLine(BigInteger.valueOf(SINGLE_SPACING));
        }
        case "chapter_title", "cover_title", "part_title" -> {
            id.setFirstLine(BigInteger.ZERO);
            para.setAlignment(ParagraphAlignment.CENTER);
            sp.setBefore(BigInteger.valueOf(480));
            sp.setAfter(BigInteger.valueOf(240));
            sp.setLine(BigInteger.valueOf(SINGLE_SPACING));
        }
        case "chapter_subtitle", "cover_subtitle", "part_subtitle" -> {
            id.setFirstLine(BigInteger.ZERO);
            para.setAlignment(ParagraphAlignment.CENTER);
            sp.setBefore(BigInteger.ZERO);
            sp.setAfter(BigInteger.valueOf(240));
            sp.setLine(BigInteger.valueOf(SINGLE_SPACING));
        }
        // "normal", "emphasis", "h1"–"h3" in a p tag → defaults above are fine
        }
    }

    private void applyBlockquoteStyle(XWPFParagraph para) {
        CTPPr     pPr = ppr(para);
        CTInd     id  = ind(pPr);
        CTSpacing sp  = spacing(pPr);
        id.setFirstLine(BigInteger.ZERO);
        id.setLeft(BigInteger.valueOf(BLOCKQUOTE_INDENT));
        id.setRight(BigInteger.valueOf(BLOCKQUOTE_INDENT));
        sp.setLine(BigInteger.valueOf(SINGLE_SPACING));
        sp.setLineRule(STLineSpacingRule.AUTO);
        sp.setBefore(BigInteger.valueOf(120));
        sp.setAfter(BigInteger.valueOf(120));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Creates a heading run on an already-configured paragraph. */
    private void headingRun(XWPFParagraph para, String text, int sizePt,
            boolean bold, boolean italic) {
        XWPFRun run = para.createRun();
        run.setFontFamily(DEFAULT_FONT);
        run.setFontSize(sizePt);
        run.setBold(bold);
        run.setItalic(italic);
        run.setText(text);
    }

    /**
     * Reads image dimensions via ImageIO. Falls back to content-width × 4:3
     * when the image cannot be decoded. Returns [widthIn, heightIn].
     */
    private double[] imageDimensions(byte[] bytes, double maxWidthIn) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null) {
                double wIn = img.getWidth() / 96.0;
                double hIn = img.getHeight() / 96.0;
                if (wIn > maxWidthIn) {
                    double scale = maxWidthIn / wIn;
                    return new double[] { maxWidthIn, hIn * scale };
                }
                return new double[] { wIn, hIn };
            }
        } catch (Exception e) {
            logger.debug("Could not read image dimensions: {}", e.getMessage());
        }
        return new double[] { maxWidthIn, maxWidthIn * 0.75 };
    }

    private PictureType mimeToPoiType(String mime) {
        if (mime == null)
            return PictureType.JPEG;
        return switch (mime.toLowerCase()) {
        case "image/jpeg", "image/jpg" -> PictureType.JPEG;
        case "image/png" -> PictureType.PNG;
        case "image/gif" -> PictureType.GIF;
        case "image/tiff" -> PictureType.TIFF;
        case "image/bmp" -> PictureType.BMP;
        default -> PictureType.JPEG;
        };
    }

    /**
     * Reads an inline {@code style="text-align: ..."} attribute from a block
     * element and returns the matching {@link ParagraphAlignment}, or
     * {@code null} if no text-align is present. Used for template HTML which
     * uses inline styles rather than {@code data-style} attributes.
     */
    private ParagraphAlignment styleAlignment(Element el) {
        String style = el.attr("style");
        if (style.isEmpty())
            return null;
        // Accept both "text-align:center" and "text-align: center" forms.
        String normalized = style.replace(" ", "");
        if (normalized.contains("text-align:center"))
            return ParagraphAlignment.CENTER;
        if (normalized.contains("text-align:right"))
            return ParagraphAlignment.RIGHT;
        if (normalized.contains("text-align:left"))
            return ParagraphAlignment.LEFT;
        return null;
    }

    /** Gets-or-creates the CTPPr (paragraph properties) for a paragraph. */
    private CTPPr ppr(XWPFParagraph para) {
        CTPPr pPr = para.getCTP().getPPr();
        return pPr != null ? pPr : para.getCTP().addNewPPr();
    }

    /** Gets-or-creates the CTSpacing block inside a CTPPr. */
    private CTSpacing spacing(CTPPr pPr) {
        return pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
    }

    /** Gets-or-creates the CTInd block inside a CTPPr. */
    private CTInd ind(CTPPr pPr) {
        return pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
    }

    private byte[] toBytes(XWPFDocument doc) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.write(out);
            doc.close();
            return out.toByteArray();
        }
    }

    /**
     * Builds the book-slug portion of the export filename.
     * Prefers {@code shortTitle} when set; falls back to {@code title}.
     * Spaces become underscores; filesystem-illegal characters are stripped.
     */
    private String bookSlug(Book book) {
        String base = (book.getShortTitle() != null && !book.getShortTitle().isBlank())
                ? book.getShortTitle()
                : book.getTitle();
        if (base == null || base.isBlank())
            return "export";
        return base.replace(' ', '_').replaceAll("[\\\\/:*?\"<>|]", "");
    }

    /** Current local time formatted as {@code yyyyMMdd-HHmmss}. */
    private String fileTimestamp() {
        return DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
    }

    /**
     * Builds a full {@code .docx} filename: {@code {bookSlug}[-qualifier]-{timestamp}.docx}.
     * {@code qualifier} (e.g. a chapter or part label) may be {@code null}.
     */
    private String docxFilename(Book book, String qualifier) {
        StringBuilder sb = new StringBuilder(bookSlug(book));
        if (qualifier != null && !qualifier.isBlank()) {
            sb.append('-').append(
                    qualifier.replace(' ', '_').replaceAll("[\\\\/:*?\"<>|]", ""));
        }
        sb.append('-').append(fileTimestamp()).append(".docx");
        return sb.toString();
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