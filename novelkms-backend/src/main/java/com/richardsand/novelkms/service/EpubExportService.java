package com.richardsand.novelkms.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.richardsand.novelkms.dao.PartDao;
import com.richardsand.novelkms.dao.ProjectDao;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.dao.book.BookDao;
import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.model.Part;
import com.richardsand.novelkms.model.Project;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;

/**
 * Simple, data-driven EPUB 3 export for full books.
 *
 * <p>No user options are supported by design. Title, author, cover art, and
 * manuscript structure all come from the database. Page layout settings are
 * intentionally ignored because this is a reflowable reading-copy export, not a
 * fixed-layout/typesetting engine.</p>
 */
public class EpubExportService {

    private static final Logger logger = LoggerFactory.getLogger(EpubExportService.class);

    private static final String EPUB_MIME = "application/epub+zip";
    private static final String BOOK_LANG = "en";

    private final BookDao    bookDao;
    private final PartDao    partDao;
    private final ChapterDao chapterDao;
    private final SceneDao   sceneDao;
    private final ProjectDao projectDao;

    public EpubExportService(BookDao bookDao, PartDao partDao, ChapterDao chapterDao,
            SceneDao sceneDao, ProjectDao projectDao) {
        this.bookDao = bookDao;
        this.partDao = partDao;
        this.chapterDao = chapterDao;
        this.sceneDao = sceneDao;
        this.projectDao = projectDao;
    }

    public record ExportMeta(byte[] bytes, String filename) {
    }

    private record EpubAsset(String id, String href, String mediaType, byte[] bytes) {
    }

    private record SpineItem(String id, String href, String title, String html) {
    }

    /** Exports a complete book as a single .epub file. */
    public ExportMeta exportBook(UUID bookId) throws Exception {
        logger.info("Starting EPUB export: bookId={}", bookId);
        Book book = requireBook(bookId);
        Project project = loadProject(book);

        List<EpubAsset> assets = new ArrayList<>();
        List<SpineItem> spine = new ArrayList<>();

        BookDao.CoverImage coverImage = loadCoverImage(book);
        if (coverImage != null) {
            String ext = extensionForMime(coverImage.mimeType());
            String href = "images/cover" + ext;
            assets.add(new EpubAsset("cover-image", href, safeMime(coverImage.mimeType()), coverImage.data()));
            spine.add(new SpineItem("cover", "cover.xhtml", "Cover", coverPageWithImage(book, project, href)));
        } else {
            spine.add(new SpineItem("cover", "cover.xhtml", "Cover", generatedTitlePage(book, project)));
        }

        List<Part> parts = partDao.findByBookId(bookId);
        List<Chapter> directChapters = chapterDao.findByBookId(bookId);
        logger.debug("EPUB export structure loaded: bookId={}, parts={}, directChapters={}, hasCoverImage={}", bookId, parts.size(), directChapters.size(), coverImage != null);

        int chapterSeq = 1;
        int partSeq = 1;

        if (!parts.isEmpty()) {
            for (Part part : parts) {
                String partHref = String.format("part-%03d.xhtml", partSeq);
                String partTitle = partLabel(part);
                spine.add(new SpineItem("part-" + partSeq, partHref, partTitle, partPage(part)));

                for (Chapter chapter : chapterDao.findByPartId(part.getId())) {
                    chapterSeq = appendChapter(spine, assets, chapter, chapterSeq);
                }
                partSeq++;
            }
        }

        for (Chapter chapter : directChapters) {
            chapterSeq = appendChapter(spine, assets, chapter, chapterSeq);
        }

        byte[] bytes = buildZip(book, project, spine, assets, coverImage != null);
        return new ExportMeta(bytes, epubFilename(book));
    }

    private int appendChapter(List<SpineItem> spine, List<EpubAsset> assets,
            Chapter chapter, int chapterSeq) throws Exception {
        String href = String.format("chapter-%03d.xhtml", chapterSeq);
        String title = chapterLabel(chapter);
        String html = chapterPage(chapter, href, assets);
        spine.add(new SpineItem("chapter-" + chapterSeq, href, title, html));
        return chapterSeq + 1;
    }

    // =========================================================================
    // EPUB package
    // =========================================================================

    private byte[] buildZip(Book book, Project project, List<SpineItem> spine,
            List<EpubAsset> assets, boolean hasCoverImage) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            addStoredMimetype(zip);
            addText(zip, "META-INF/container.xml", containerXml());
            addText(zip, "EPUB/styles.css", stylesCss());
            addText(zip, "EPUB/nav.xhtml", navXhtml(book, spine));
            addText(zip, "EPUB/package.opf", packageOpf(book, project, spine, assets, hasCoverImage));

            for (SpineItem item : spine) {
                addText(zip, "EPUB/" + item.href(), item.html());
            }
            for (EpubAsset asset : assets) {
                addBytes(zip, "EPUB/" + asset.href(), asset.bytes());
            }
        }
        return out.toByteArray();
    }

    private void addStoredMimetype(ZipOutputStream zip) throws Exception {
        byte[] bytes = EPUB_MIME.getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(bytes);

        ZipEntry entry = new ZipEntry("mimetype");
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCompressedSize(bytes.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private void addText(ZipOutputStream zip, String path, String text) throws Exception {
        addBytes(zip, path, text.getBytes(StandardCharsets.UTF_8));
    }

    private void addBytes(ZipOutputStream zip, String path, byte[] bytes) throws Exception {
        ZipEntry entry = new ZipEntry(path);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private String containerXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n"
                + "  <rootfiles>\n"
                + "    <rootfile full-path=\"EPUB/package.opf\" media-type=\"application/oebps-package+xml\"/>\n"
                + "  </rootfiles>\n"
                + "</container>\n";
    }

    private String packageOpf(Book book, Project project, List<SpineItem> spine,
            List<EpubAsset> assets, boolean hasCoverImage) {
        StringBuilder manifest = new StringBuilder();
        manifest.append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n");
        manifest.append("    <item id=\"css\" href=\"styles.css\" media-type=\"text/css\"/>\n");
        for (SpineItem item : spine) {
            manifest.append("    <item id=\"").append(xml(item.id())).append("\" href=\"")
                    .append(xml(item.href())).append("\" media-type=\"application/xhtml+xml\"/>\n");
        }
        for (EpubAsset asset : assets) {
            manifest.append("    <item id=\"").append(xml(asset.id())).append("\" href=\"")
                    .append(xml(asset.href())).append("\" media-type=\"")
                    .append(xml(asset.mediaType())).append("\"");
            if (hasCoverImage && "cover-image".equals(asset.id())) {
                manifest.append(" properties=\"cover-image\"");
            }
            manifest.append("/>\n");
        }

        StringBuilder spineXml = new StringBuilder();
        for (SpineItem item : spine) {
            spineXml.append("    <itemref idref=\"").append(xml(item.id())).append("\"/>\n");
        }

        String modified = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String author = authorName(project);

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<package version=\"3.0\" unique-identifier=\"book-id\" xmlns=\"http://www.idpf.org/2007/opf\">\n"
                + "  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n"
                + "    <dc:identifier id=\"book-id\">urn:uuid:" + xml(book.getId().toString()) + "</dc:identifier>\n"
                + "    <dc:title>" + xml(titleOrUntitled(book.getTitle())) + "</dc:title>\n"
                + (author.isBlank() ? "" : "    <dc:creator>" + xml(author) + "</dc:creator>\n")
                + "    <dc:language>" + BOOK_LANG + "</dc:language>\n"
                + "    <meta property=\"dcterms:modified\">" + modified + "</meta>\n"
                + (hasCoverImage ? "    <meta name=\"cover\" content=\"cover-image\"/>\n" : "")
                + "  </metadata>\n"
                + "  <manifest>\n" + manifest
                + "  </manifest>\n"
                + "  <spine>\n" + spineXml
                + "  </spine>\n"
                + "</package>\n";
    }

    private String navXhtml(Book book, List<SpineItem> spine) {
        StringBuilder items = new StringBuilder();
        for (SpineItem item : spine) {
            if ("cover".equals(item.id()))
                continue;
            items.append("      <li><a href=\"").append(xml(item.href())).append("\">")
                    .append(xml(item.title())).append("</a></li>\n");
        }

        return xhtmlPage(titleOrUntitled(book.getTitle()) + " - Table of Contents",
                "<nav epub:type=\"toc\" id=\"toc\">\n"
                        + "  <h1>Table of Contents</h1>\n"
                        + "  <ol>\n"
                        + items
                        + "  </ol>\n"
                        + "</nav>\n");
    }

    // =========================================================================
    // Content pages
    // =========================================================================

    private String coverPageWithImage(Book book, Project project, String imageHref) {
        return xhtmlPage(titleOrUntitled(book.getTitle()),
                "<section class=\"cover\">\n"
                        + "  <img class=\"cover-image\" src=\"" + xml(imageHref) + "\" alt=\"Cover\"/>\n"
                        + "</section>\n");
    }

    private String generatedTitlePage(Book book, Project project) {
        StringBuilder body = new StringBuilder();
        body.append("<section class=\"title-page\">\n");
        body.append("  <h1>").append(xml(titleOrUntitled(book.getTitle()))).append("</h1>\n");
        if (notBlank(book.getSubtitle())) {
            body.append("  <p class=\"subtitle\">").append(xml(book.getSubtitle())).append("</p>\n");
        }
        String author = authorName(project);
        if (!author.isBlank()) {
            body.append("  <p class=\"author\">By ").append(xml(author)).append("</p>\n");
        }
        body.append("</section>\n");
        return xhtmlPage(titleOrUntitled(book.getTitle()), body.toString());
    }

    private String partPage(Part part) {
        StringBuilder body = new StringBuilder();
        body.append("<section class=\"part-page\">\n");
        body.append("  <h1>").append(xml(partLabel(part))).append("</h1>\n");
        if (notBlank(part.getSubtitle())) {
            body.append("  <p class=\"subtitle\">").append(xml(part.getSubtitle())).append("</p>\n");
        }
        body.append("</section>\n");
        return xhtmlPage(partLabel(part), body.toString());
    }

    private String chapterPage(Chapter chapter, String chapterHref, List<EpubAsset> assets) throws Exception {
        StringBuilder body = new StringBuilder();
        body.append("<section class=\"chapter\">\n");
        body.append("  <h1>").append(xml(chapterLabel(chapter))).append("</h1>\n");
        if (notBlank(chapter.getSubtitle())) {
            body.append("  <p class=\"chapter-subtitle\">").append(xml(chapter.getSubtitle())).append("</p>\n");
        }

        List<Scene> scenes = sceneDao.findByChapterId(chapter.getId());
        boolean firstScene = true;
        for (Scene sceneMeta : scenes) {
            Scene scene = (sceneMeta.getContent() != null && !sceneMeta.getContent().isBlank())
                    ? sceneMeta
                    : sceneDao.findById(sceneMeta.getId()).orElse(sceneMeta);
            if (!firstScene) {
                body.append("  <p class=\"scene-break\">* * *</p>\n");
            }
            body.append(cleanSceneHtml(scene.getContent(), chapterHref, assets));
            firstScene = false;
        }

        body.append("</section>\n");
        return xhtmlPage(chapterLabel(chapter), body.toString());
    }

    private String xhtmlPage(String title, String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE html>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" lang=\""
                + BOOK_LANG + "\" xml:lang=\"" + BOOK_LANG + "\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\"/>\n"
                + "  <title>" + xml(title) + "</title>\n"
                + "  <link rel=\"stylesheet\" type=\"text/css\" href=\"styles.css\"/>\n"
                + "</head>\n"
                + "<body>\n"
                + body
                + "</body>\n"
                + "</html>\n";
    }

    // =========================================================================
    // TipTap HTML cleanup + asset extraction
    // =========================================================================

    private String cleanSceneHtml(String html, String chapterHref, List<EpubAsset> assets) {
        if (html == null || html.isBlank())
            return "";

        Document doc = Jsoup.parseBodyFragment(html);
        StringBuilder out = new StringBuilder();
        for (Element child : doc.body().children()) {
            renderBlock(child, out, chapterHref, assets);
        }
        return out.toString();
    }

    private void renderBlock(Element el, StringBuilder out, String chapterHref, List<EpubAsset> assets) {
        String tag = el.tagName().toLowerCase();
        switch (tag) {
        case "p" -> renderParagraph(el, out, chapterHref, assets);
        case "h1", "h2", "h3" -> renderSimpleContainer(tag, el, out, chapterHref, assets, null);
        case "blockquote" -> renderSimpleContainer("blockquote", el, out, chapterHref, assets, null);
        case "ul", "ol" -> renderList(tag, el, out, chapterHref, assets);
        case "hr" -> out.append("  <p class=\"scene-break\">* * *</p>\n");
        case "img" -> renderImage(el, out, chapterHref, assets);
        default -> {
            if (el.hasText()) {
                renderParagraph(el, out, chapterHref, assets);
            }
        }
        }
    }

    private void renderParagraph(Element el, StringBuilder out, String chapterHref, List<EpubAsset> assets) {
        if (isDraftStructuralNode(el))
            return;

        String cls = classForStyle(el.attr("data-style"));
        out.append("  <p");
        if (cls != null) {
            out.append(" class=\"").append(cls).append("\"");
        }
        out.append(">");
        renderInlineChildren(el, out, chapterHref, assets);
        out.append("</p>\n");
    }

    private void renderSimpleContainer(String tag, Element el, StringBuilder out,
            String chapterHref, List<EpubAsset> assets, String cls) {
        if (isDraftStructuralNode(el))
            return;
        out.append("  <").append(tag);
        if (cls != null) {
            out.append(" class=\"").append(cls).append("\"");
        }
        out.append(">");
        renderInlineChildren(el, out, chapterHref, assets);
        out.append("</").append(tag).append(">\n");
    }

    private void renderList(String tag, Element el, StringBuilder out, String chapterHref, List<EpubAsset> assets) {
        out.append("  <").append(tag).append(">\n");
        for (Element li : el.select("> li")) {
            out.append("    <li>");
            renderInlineChildren(li, out, chapterHref, assets);
            out.append("</li>\n");
        }
        out.append("  </").append(tag).append(">\n");
    }

    private void renderInlineChildren(Element el, StringBuilder out, String chapterHref, List<EpubAsset> assets) {
        for (Node node : el.childNodes()) {
            if (node instanceof TextNode text) {
                out.append(xml(text.text()));
            } else if (node instanceof Element child) {
                renderInline(child, out, chapterHref, assets);
            }
        }
    }

    private void renderInline(Element el, StringBuilder out, String chapterHref, List<EpubAsset> assets) {
        String tag = el.tagName().toLowerCase();
        switch (tag) {
        case "strong", "b" -> wrapInline("strong", el, out, chapterHref, assets);
        case "em", "i" -> wrapInline("em", el, out, chapterHref, assets);
        case "u" -> wrapInline("u", el, out, chapterHref, assets);
        case "s", "del", "strike" -> wrapInline("s", el, out, chapterHref, assets);
        case "br" -> out.append("<br/>");
        case "img" -> renderInlineImage(el, out, chapterHref, assets);
        case "span" -> renderInlineChildren(el, out, chapterHref, assets);
        default -> renderInlineChildren(el, out, chapterHref, assets);
        }
    }

    private void wrapInline(String tag, Element el, StringBuilder out, String chapterHref, List<EpubAsset> assets) {
        out.append('<').append(tag).append('>');
        renderInlineChildren(el, out, chapterHref, assets);
        out.append("</").append(tag).append('>');
    }

    private void renderImage(Element img, StringBuilder out, String chapterHref, List<EpubAsset> assets) {
        out.append("  <p class=\"image-paragraph\">");
        renderInlineImage(img, out, chapterHref, assets);
        out.append("</p>\n");
    }

    private void renderInlineImage(Element img, StringBuilder out, String chapterHref, List<EpubAsset> assets) {
        String href = extractImageAsset(img, chapterHref, assets);
        if (href == null)
            return;
        out.append("<img src=\"").append(xml(href)).append("\" alt=\"\"");
        String width = img.attr("width");
        if (notBlank(width)) {
            out.append(" style=\"width:").append(xml(width)).append("px\"");
        }
        out.append("/>");
    }

    private String extractImageAsset(Element img, String chapterHref, List<EpubAsset> assets) {
        String src = img.attr("src");
        if (src == null || !src.startsWith("data:"))
            return null;

        int comma = src.indexOf(',');
        if (comma < 0)
            return null;

        try {
            String meta = src.substring(5, comma);
            String mime = meta.contains(";") ? meta.substring(0, meta.indexOf(';')) : meta;
            byte[] bytes = Base64.getDecoder().decode(src.substring(comma + 1));

            String id = "img-" + (assets.size() + 1);
            String href = "images/" + id + extensionForMime(mime);
            assets.add(new EpubAsset(id, href, safeMime(mime), bytes));

            // Chapter XHTML files live in EPUB/. Image hrefs are relative to EPUB/.
            return href;
        } catch (Exception e) {
            logger.warn("Skipping image in EPUB export from {}: {}", chapterHref, e.getMessage());
            return null;
        }
    }

    private boolean isDraftStructuralNode(Element el) {
        return el.hasAttr("data-draft-heading") || el.hasAttr("data-scene-boundary");
    }

    private String classForStyle(String styleKey) {
        if (styleKey == null || styleKey.isBlank() || "normal".equals(styleKey))
            return null;
        return switch (styleKey) {
        case "blockquote" -> "blockquote";
        case "emphasis" -> "emphasis";
        case "report" -> "report";
        case "chapter_title" -> "chapter-title";
        case "chapter_subtitle" -> "chapter-subtitle";
        case "cover_title" -> "cover-title";
        case "cover_subtitle" -> "cover-subtitle";
        case "part_title" -> "part-title";
        case "part_subtitle" -> "part-subtitle";
        default -> null;
        };
    }

    // =========================================================================
    // CSS
    // =========================================================================

    private String stylesCss() {
        return "body {\n"
                + "  font-family: serif;\n"
                + "  line-height: 1.4;\n"
                + "}\n\n"
                + "p {\n"
                + "  margin: 0 0 1em 0;\n"
                + "  text-indent: 1.5em;\n"
                + "}\n\n"
                + "h1, h2, h3 {\n"
                + "  text-align: center;\n"
                + "  text-indent: 0;\n"
                + "  margin-top: 2em;\n"
                + "  margin-bottom: 1em;\n"
                + "}\n\n"
                + ".title-page, .part-page {\n"
                + "  text-align: center;\n"
                + "  margin-top: 25%;\n"
                + "}\n\n"
                + ".subtitle, .author, .chapter-subtitle {\n"
                + "  text-align: center;\n"
                + "  text-indent: 0;\n"
                + "}\n\n"
                + ".cover {\n"
                + "  text-align: center;\n"
                + "}\n\n"
                + ".cover-image {\n"
                + "  max-width: 100%;\n"
                + "  height: auto;\n"
                + "}\n\n"
                + "blockquote, p.blockquote {\n"
                + "  margin-left: 1.5em;\n"
                + "  margin-right: 1.5em;\n"
                + "  text-indent: 0;\n"
                + "}\n\n"
                + ".emphasis {\n"
                + "  font-style: italic;\n"
                + "}\n\n"
                + ".report {\n"
                + "  text-indent: 0;\n"
                + "  font-family: monospace;\n"
                + "}\n\n"
                + ".scene-break {\n"
                + "  text-align: center;\n"
                + "  text-indent: 0;\n"
                + "  margin: 1.5em 0;\n"
                + "}\n\n"
                + ".image-paragraph {\n"
                + "  text-align: center;\n"
                + "  text-indent: 0;\n"
                + "}\n\n"
                + "img {\n"
                + "  max-width: 100%;\n"
                + "  height: auto;\n"
                + "}\n";
    }

    // =========================================================================
    // DAO wrappers + labels
    // =========================================================================

    private Book requireBook(UUID id) throws Exception {
        return bookDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + id));
    }

    private Project loadProject(Book book) {
        if (book.getProjectId() == null)
            return null;
        try {
            return projectDao.findById(book.getProjectId()).orElse(null);
        } catch (Exception e) {
            logger.warn("Could not load project for EPUB export: {}", e.getMessage());
            return null;
        }
    }

    private BookDao.CoverImage loadCoverImage(Book book) {
        if (!book.isHasCoverImage())
            return null;
        try {
            return bookDao.getCoverImage(book.getId()).orElse(null);
        } catch (Exception e) {
            logger.warn("Could not load cover image for EPUB export: {}", e.getMessage());
            return null;
        }
    }

    private String partLabel(Part part) {
        return notBlank(part.getTitle()) ? part.getTitle() : "Part " + toRoman(part.getPartNumber());
    }

    private String chapterLabel(Chapter chapter) {
        return notBlank(chapter.getTitle()) ? chapter.getTitle() : "Chapter " + chapter.getChapterNumber();
    }

    private String authorName(Project project) {
        if (project == null)
            return "";
        if (notBlank(project.getDisplayName()))
            return project.getDisplayName().trim();
        String full = ((project.getAuthorFirstName() != null ? project.getAuthorFirstName() : "")
                + " "
                + (project.getAuthorLastName() != null ? project.getAuthorLastName() : "")).trim();
        return full;
    }

    private String titleOrUntitled(String title) {
        return notBlank(title) ? title.trim() : "Untitled";
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String safeMime(String mime) {
        return notBlank(mime) && mime.startsWith("image/") ? mime : "image/jpeg";
    }

    private String extensionForMime(String mime) {
        if (mime == null)
            return ".jpg";
        return switch (mime.toLowerCase()) {
        case "image/png" -> ".png";
        case "image/gif" -> ".gif";
        case "image/svg+xml" -> ".svg";
        case "image/webp" -> ".webp";
        case "image/jpeg", "image/jpg" -> ".jpg";
        default -> ".jpg";
        };
    }

    private String epubFilename(Book book) {
        return bookSlug(book) + '-' + fileTimestamp() + ".epub";
    }

    private String bookSlug(Book book) {
        String base = notBlank(book.getShortTitle()) ? book.getShortTitle() : book.getTitle();
        if (!notBlank(base))
            return "export";
        return base.replace(' ', '_').replaceAll("[\\\\/:*?\"<>|]", "");
    }

    private String fileTimestamp() {
        return DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
    }

    private String toRoman(int n) {
        if (n <= 0)
            return String.valueOf(n);
        int[] vals = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
        String[] syms = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vals.length; i++) {
            while (n >= vals[i]) {
                sb.append(syms[i]);
                n -= vals[i];
            }
        }
        return sb.toString();
    }

    private String xml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
