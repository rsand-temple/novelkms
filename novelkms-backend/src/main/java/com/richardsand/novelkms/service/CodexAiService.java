package com.richardsand.novelkms.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.ai.AiProvider;
import com.richardsand.novelkms.ai.AiProviderException;
import com.richardsand.novelkms.ai.CodexFillRequest;
import com.richardsand.novelkms.ai.CodexFillResult;
import com.richardsand.novelkms.ai.ReviewException;
import com.richardsand.novelkms.dao.SceneDao;
import com.richardsand.novelkms.dao.ai.AiCredentialDao;
import com.richardsand.novelkms.dao.book.BookDao;
import com.richardsand.novelkms.dao.chapter.ChapterDao;
import com.richardsand.novelkms.dao.chapter.ChapterSummaryDao;
import com.richardsand.novelkms.dao.codex.CodexDao;
import com.richardsand.novelkms.dao.codex.CodexTypeFieldDao;
import com.richardsand.novelkms.model.Scene;
import com.richardsand.novelkms.model.ai.AiCredential;
import com.richardsand.novelkms.model.book.Book;
import com.richardsand.novelkms.model.chapter.Chapter;
import com.richardsand.novelkms.model.codex.Codex;
import com.richardsand.novelkms.model.codex.CodexField;

import jakarta.ws.rs.core.Response.Status;

/**
 * Orchestrates AI-driven fill-in of a single codex entry. The service:
 * <ol>
 * <li>Resolves the entry's field schema from its own Type instance (the parent
 * category chapter's {@code codex_type_field} rows), not the system-global
 * category master.</li>
 * <li>Assembles manuscript context from chapter summaries (filtered to the
 * author-selected chapters when {@code selectedChapterIds} is provided).</li>
 * <li>Assembles pinned codex entries as reference context.</li>
 * <li>Calls the resolved {@link AiProvider#fillCodexEntry} method.</li>
 * <li>Returns the {@link CodexFillResult} for the caller (resource) to relay
 * to the frontend without auto-saving, so the author reviews the
 * suggestions before they are committed.</li>
 * </ol>
 *
 * <p>
 * <b>Codex scope.</b> A codex is scoped to exactly one project (series-wide)
 * or one book — exactly one of {@code projectId}/{@code bookId} is set on the
 * {@link Codex} row. Manuscript context is assembled to match:
 * <ul>
 * <li><b>Book-scoped codex</b> ({@code bookId != null}): chapter summaries of
 * that one book, in reading order.</li>
 * <li><b>Project-scoped codex</b> ({@code projectId != null}): chapter
 * summaries of every book in the project, concatenated book by book under a
 * book heading.</li>
 * </ul>
 *
 * <p>
 * <b>Chapter selection.</b> The frontend supplies a {@code selectedChapterIds}
 * list identifying which chapters the author wants to use as context.
 * Only chapters in that set (and that have a summary) contribute to
 * {@code manuscriptContext}. When the list is null or empty the service falls
 * back to all available summaries (backward-compatibility path). A
 * {@link ReviewException} with key {@code no_chapter_summaries} is thrown only
 * when the resulting context is empty, i.e. none of the selected chapters have
 * a summary.
 */
public class CodexAiService {

	private static final Logger       logger = LoggerFactory.getLogger(CodexAiService.class);
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** Maximum characters of chapter summary text sent as manuscript context. */
	private static final int MAX_MANUSCRIPT_CHARS = 40_000;

	// =========================================================================
	// Public record — chapter info for the dialog endpoint
	// =========================================================================

	/**
	 * One chapter's summary status within the codex's scope, used by
	 * {@link #listChaptersForCodexEntry} to populate the
	 * {@code GET /scenes/{sceneId}/codex-chapters} response.
	 *
	 * <p>{@code bookTitle} is non-null only for project-scoped codexes with more
	 * than one book; the frontend uses it to render book-group headings.
	 * {@code isStale} is true when the chapter's scenes were edited after the
	 * summary was generated. {@code hasSummary} is false when the chapter has no
	 * summary for any provider.
	 */
	public record CodexChapterInfo(
			UUID   chapterId,
			int    seq,
			int    chapterNumber,
			String title,
			UUID   bookId,
			String bookTitle,
			boolean hasSummary,
			boolean isStale,
			String  provider) {
	}

	// =========================================================================
	// Fields and constructor
	// =========================================================================

	private final SceneDao                sceneDao;
	private final ChapterDao              chapterDao;
	private final BookDao                 bookDao;
	private final CodexDao                codexDao;
	private final CodexTypeFieldDao       codexTypeFieldDao;
	private final AiCredentialDao         credentialDao;
	private final ChapterSummaryDao       chapterSummaryDao;
	private final Map<String, AiProvider> providers;

	public CodexAiService(SceneDao sceneDao, ChapterDao chapterDao, BookDao bookDao,
			CodexDao codexDao, CodexTypeFieldDao codexTypeFieldDao,
			AiCredentialDao credentialDao, ChapterSummaryDao chapterSummaryDao,
			Map<String, AiProvider> providers) {
		this.sceneDao = sceneDao;
		this.chapterDao = chapterDao;
		this.bookDao = bookDao;
		this.codexDao = codexDao;
		this.codexTypeFieldDao = codexTypeFieldDao;
		this.credentialDao = credentialDao;
		this.chapterSummaryDao = chapterSummaryDao;
		this.providers = providers;
	}

	// =========================================================================
	// Public API
	// =========================================================================

	/**
	 * Returns the chapter list for the given codex entry's scope, each row
	 * carrying its summary status. Called by the chapter-selection dialog before
	 * the author initiates a fill.
	 *
	 * <p>The preferred provider is resolved from the user's default credential.
	 * When no credential is configured, any available provider variant is used.
	 * Returns an empty list (never throws) when the scene is not a codex entry or
	 * the codex cannot be located, so the frontend can display an appropriate
	 * empty state.
	 *
	 * @param userId  authenticated user requesting the list
	 * @param sceneId codex entry scene whose parent codex determines the scope
	 * @return chapters in reading order, with {@link CodexChapterInfo} per chapter
	 */
	public List<CodexChapterInfo> listChaptersForCodexEntry(UUID userId, UUID sceneId)
			throws SQLException {

		// ── Load hierarchy ──────────────────────────────────────────────────
		Scene scene = sceneDao.findById(sceneId).orElse(null);
		if (scene == null) return List.of();

		UUID chapterId = scene.getChapterId();
		if (chapterId == null) return List.of();

		Chapter chapter = chapterDao.findById(chapterId).orElse(null);
		if (chapter == null || chapter.getCodexId() == null) return List.of();

		Codex codex = codexDao.findById(chapter.getCodexId()).orElse(null);
		if (codex == null) return List.of();

		// ── Resolve preferred provider ──────────────────────────────────────
		// Failures here are non-fatal: show chapters with whatever summary variant exists.
		String preferredProvider = null;
		try {
			AiCredential cred = resolveCredential(userId, null);
			preferredProvider = cred.getProvider();
		} catch (ReviewException ignored) {
			// No credential configured — use null (most-recently-updated variant).
		}

		// ── Assemble chapter rows ───────────────────────────────────────────
		List<CodexChapterInfo> result = new ArrayList<>();

		if (codex.getBookId() != null) {
			// Book-scoped: one book, no book-title grouping.
			for (ChapterSummaryDao.Row row : chapterSummaryDao.bookChapterSummaries(
					codex.getBookId(), preferredProvider)) {
				result.add(toChapterInfo(row, codex.getBookId(), null));
			}
		} else if (codex.getProjectId() != null) {
			// Project-scoped: every book in the project.
			List<Book> books     = bookDao.findByProjectId(codex.getProjectId());
			boolean    multiBook = books.size() > 1;
			for (Book book : books) {
				String bookTitle = multiBook
						? (book.getTitle() != null && !book.getTitle().isBlank()
								? book.getTitle().trim()
								: "Untitled Book")
						: null;
				for (ChapterSummaryDao.Row row : chapterSummaryDao.bookChapterSummaries(
						book.getId(), preferredProvider)) {
					result.add(toChapterInfo(row, book.getId(), bookTitle));
				}
			}
		}

		return result;
	}

	/**
	 * Generates AI-suggested field values and a body description for the given
	 * codex entry scene and returns them without saving.
	 *
	 * @param userId               authenticated user requesting the fill
	 * @param sceneId              codex entry scene to fill
	 * @param credentialId         specific credential to use; null = user's default
	 * @param userGuidance         optional one-time author note for this call
	 * @param selectedChapterIds   chapters whose summaries should be included as
	 *                             manuscript context; null or empty = all available
	 * @return suggested fields and body — the caller/resource passes these to the
	 *         frontend, which applies them to the form and triggers autosave
	 * @throws SQLException    if a DAO operation fails
	 * @throws ReviewException for all configuration/validation problems
	 */
	public CodexFillResult fillCodexEntry(UUID userId, UUID sceneId,
			UUID credentialId, String userGuidance, List<UUID> selectedChapterIds)
			throws SQLException {

		logger.info("Codex AI fill started: userId={}, sceneId={}, credentialId={}, selectedChapterCount={}",
				userId, sceneId, credentialId,
				selectedChapterIds == null ? "all" : selectedChapterIds.size());

		// ── Load the entry and its hierarchy ───────────────────────────────
		Scene scene = sceneDao.findById(sceneId)
				.orElseThrow(() -> new ReviewException(Status.BAD_REQUEST,
						"not_found", "Codex entry not found."));

		UUID    chapterId = scene.getChapterId();
		Chapter chapter   = chapterId == null ? null
				: chapterDao.findById(chapterId).orElse(null);
		if (chapter == null || chapter.getCodexId() == null) {
			throw new ReviewException(Status.BAD_REQUEST, "not_codex_entry",
					"This scene is not a codex entry — AI fill is only available for codex entries.");
		}

		Codex codex = codexDao.findById(chapter.getCodexId())
				.orElseThrow(() -> new ReviewException(Status.BAD_REQUEST,
						"not_found", "The entry's codex could not be found."));

		// ── Resolve the Type's fields (per-instance schema) and label ───────
		// The entry's parent chapter IS its Type; its active fields are the
		// schema. The Type name (chapter.title) is the author-facing label.
		String           categoryKey   = chapter.getCodexCategory();
		List<CodexField> fields        = codexTypeFieldDao.findActiveByType(chapterId);
		String           categoryLabel = firstNonBlank(chapter.getTitle(), categoryKey);
		if (categoryLabel.isBlank()) {
			categoryLabel = "Codex Entry";
		}

		// ── Resolve AI credential and provider ──────────────────────────────
		AiCredential credential = resolveCredential(userId, credentialId);
		AiProvider   provider   = providers.get(credential.getProvider());
		if (provider == null) {
			throw new ReviewException(Status.BAD_REQUEST, "unsupported_provider",
					"Provider " + credential.getProvider() + " is not supported.");
		}
		String model  = firstNonBlank(credential.getDefaultModel(), provider.defaultModel());
		String apiKey = credentialDao.getDecryptedKey(credential.getId(), userId);
		if (apiKey == null || apiKey.isBlank()) {
			throw new ReviewException(Status.CONFLICT, "no_ai_credential",
					"Stored API key could not be read.");
		}

		// ── Assemble manuscript context from selected chapter summaries ──────
		// Convert list to set for O(1) lookup; null/empty = use all chapters.
		Set<UUID> selectedSet = (selectedChapterIds != null && !selectedChapterIds.isEmpty())
				? new HashSet<>(selectedChapterIds)
				: null;

		String manuscriptContext = assembleManuscriptContext(codex, credential.getProvider(), selectedSet);

		if (manuscriptContext == null) {
			String scope = codex.getBookId() != null ? "book" : "project";
			String msg   = selectedSet != null
					? "None of the selected chapters have summaries. Generate a summary for at least one "
					  + "selected chapter, then try again."
					: "Chapter summaries are required for AI codex fill, and none were found for this "
					  + scope + ". Please generate chapter summaries (right-click a chapter → "
					  + "Generate chapter summary), then try again.";
			throw new ReviewException(Status.BAD_REQUEST, "no_chapter_summaries", msg);
		}

		// ── Assemble pinned codex reference context ─────────────────────────
		String referenceContext = assembleReferenceContext(chapter.getCodexId(),
				categoryKey, scene.getTitle());

		// ── Build prompt ingredients ────────────────────────────────────────
		String schemaDescription = buildSchemaDescription(fields);
		String existingFields    = buildExistingFieldsText(fields, scene.getStructuredData());
		String entryTitle        = scene.getTitle() != null && !scene.getTitle().isBlank()
				? scene.getTitle().trim()
				: "Untitled";

		logger.debug("Codex AI fill context assembled: entryTitle={}, provider={}, model={}, "
				+ "codexScope={}, schemaFields={}, manuscriptContextChars={}, referenceContextChars={}, "
				+ "userGuidanceChars={}",
				entryTitle, credential.getProvider(), model,
				codex.getBookId() != null ? "BOOK" : "PROJECT",
				fields.size(),
				manuscriptContext.length(),
				referenceContext == null ? 0 : referenceContext.length(),
				userGuidance == null ? 0 : userGuidance.length());

		CodexFillRequest req = new CodexFillRequest(
				apiKey, model, entryTitle, categoryLabel,
				schemaDescription, existingFields,
				manuscriptContext, referenceContext,
				blankToNull(userGuidance));

		// ── Call provider ───────────────────────────────────────────────────
		try {
			CodexFillResult result = provider.fillCodexEntry(req);
			logger.info("Codex AI fill completed: sceneId={}, provider={}, fieldCount={}",
					sceneId, credential.getProvider(), result.fields().size());
			return result;
		} catch (AiProviderException e) {
			logger.error("Codex AI fill provider error: sceneId={}, provider={}, error={}",
					sceneId, credential.getProvider(), e.getMessage());
			throw new ReviewException(Status.INTERNAL_SERVER_ERROR, "provider_error", e.getMessage());
		}
	}

	// =========================================================================
	// Context assembly
	// =========================================================================

	/**
	 * Assembles chapter summaries as manuscript context, matching the codex's
	 * scope and filtering to the author-selected chapters when a non-empty
	 * {@code selectedChapterIds} set is provided.
	 *
	 * <ul>
	 * <li>Book-scoped codex: the summaries of that one book.</li>
	 * <li>Project-scoped codex: the summaries of every book in the project,
	 * concatenated book by book under a book heading.</li>
	 * </ul>
	 *
	 * Returns null when no chapter with a summary falls within the effective
	 * selection, so the caller can emit {@code no_chapter_summaries}. Text is
	 * capped at {@link #MAX_MANUSCRIPT_CHARS}.
	 */
	private String assembleManuscriptContext(Codex codex, String provider,
			Set<UUID> selectedChapterIds) throws SQLException {
		StringBuilder sb = new StringBuilder();

		if (codex.getBookId() != null) {
			appendBookSummaries(sb, codex.getBookId(), null, provider, selectedChapterIds);
		} else if (codex.getProjectId() != null) {
			List<Book> books     = bookDao.findByProjectId(codex.getProjectId());
			boolean    multiBook = books.size() > 1;
			for (Book book : books) {
				String bookHeading = multiBook
						? (book.getTitle() != null && !book.getTitle().isBlank()
								? book.getTitle().trim()
								: "Untitled Book")
						: null;
				appendBookSummaries(sb, book.getId(), bookHeading, provider, selectedChapterIds);
			}
		}

		String result = sb.toString().strip();
		if (result.isEmpty()) return null;

		if (result.length() > MAX_MANUSCRIPT_CHARS) {
			result = result.substring(0, MAX_MANUSCRIPT_CHARS) + "\n[Context truncated]";
		}
		return result;
	}

	/**
	 * Appends one book's chapter summaries (in reading order) to {@code sb}.
	 * Chapters without a summary are skipped. When {@code selectedChapterIds} is
	 * non-null and non-empty, chapters not in the set are also skipped — these
	 * are chapters the author deselected in the fill dialog.
	 *
	 * <p>When {@code bookHeading} is non-null (project-scoped, multi-book codex),
	 * a "## Book Title" heading precedes the first appended chapter so the model
	 * can distinguish between books.
	 */
	private void appendBookSummaries(StringBuilder sb, UUID bookId, String bookHeading,
			String provider, Set<UUID> selectedChapterIds) throws SQLException {
		List<ChapterSummaryDao.Row> rows = chapterSummaryDao.bookChapterSummaries(bookId, provider);

		boolean wroteHeading = false;
		for (ChapterSummaryDao.Row row : rows) {
			if (row.content() == null || row.content().isBlank()) continue;

			// Respect author-selected chapters when a selection is provided.
			if (selectedChapterIds != null && !selectedChapterIds.isEmpty()
					&& !selectedChapterIds.contains(row.chapterId())) {
				continue;
			}

			if (bookHeading != null && !wroteHeading) {
				sb.append("## ").append(bookHeading).append("\n\n");
				wroteHeading = true;
			}

			String label = (row.title() != null && !row.title().isBlank())
					? "Chapter " + row.chapterNumber() + ": " + row.title()
					: "Chapter " + row.chapterNumber();
			sb.append(label).append("\n");
			sb.append(htmlToPlainText(row.content())).append("\n\n");
		}
	}

	/**
	 * Assembles pinned codex entries from the same codex as reference context,
	 * excluding the entry being filled (to avoid circular self-reference).
	 * Returns null when no pinned entries have content.
	 */
	private String assembleReferenceContext(UUID codexId, String excludedCategoryKey,
			String excludedTitle) throws SQLException {
		List<CodexDao.AiContextEntry> entries = codexDao.listPinnedAiContextEntries(codexId);
		if (entries.isEmpty()) return null;

		// Each pinned entry's parent chapter is its Type; render its structured
		// fields from that Type's own active fields. Cache per Type so entries
		// sharing a Type don't re-query.
		Map<UUID, List<CodexField>> fieldsByType = new HashMap<>();

		StringBuilder sb      = new StringBuilder();
		String        lastCat = null;
		for (CodexDao.AiContextEntry e : entries) {
			// Skip the entry being filled if it is itself pinned
			if (excludedTitle != null
					&& excludedCategoryKey != null
					&& excludedCategoryKey.equals(e.categoryKey())
					&& excludedTitle.equalsIgnoreCase(e.title())) {
				continue;
			}

			List<CodexField> typeFields = fieldsByType.get(e.chapterId());
			if (typeFields == null) {
				typeFields = codexTypeFieldDao.findActiveByType(e.chapterId());
				fieldsByType.put(e.chapterId(), typeFields);
			}

			String structured = renderStructuredFields(typeFields, e.structuredData());
			String plain      = htmlToPlainText(e.content());
			if (structured.isBlank() && plain.isBlank()) continue;

			String cat = (e.categoryTitle() != null && !e.categoryTitle().isBlank())
					? e.categoryTitle().trim()
					: "Codex";
			if (!cat.equals(lastCat)) {
				sb.append("# ").append(cat).append("\n\n");
				lastCat = cat;
			}

			String title = (e.title() != null && !e.title().isBlank())
					? e.title().trim()
					: "Untitled";
			sb.append("=== ").append(title).append(" ===\n");
			if (!structured.isBlank()) sb.append(structured).append("\n");
			if (!plain.isBlank()) sb.append(plain).append("\n");
			sb.append("\n");
		}

		String result = sb.toString().strip();
		return result.isEmpty() ? null : result;
	}

	private String renderStructuredFields(List<CodexField> fields, String structuredJson) {
		if (fields == null || fields.isEmpty()
				|| structuredJson == null || structuredJson.isBlank()) {
			return "";
		}
		Map<String, Object> values;
		try {
			values = MAPPER.readValue(structuredJson, new TypeReference<Map<String, Object>>() {
			});
		} catch (Exception e) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (CodexField field : fields) {
			if (!field.isFeedsAi()) continue;
			Object raw   = values.get(field.getKey());
			String value = raw == null ? "" : raw.toString().trim();
			if (value.isEmpty()) continue;
			String label = field.getLabel() != null ? field.getLabel() : field.getKey();
			sb.append(label).append(": ").append(value).append("\n");
		}
		return sb.toString().strip();
	}

	/**
	 * Maps a {@link ChapterSummaryDao.Row} to a {@link CodexChapterInfo}
	 * for the chapter-selection dialog response.
	 */
	private static CodexChapterInfo toChapterInfo(ChapterSummaryDao.Row row,
			UUID bookId, String bookTitle) {
		boolean hasSummary = row.generatedAt() != null;
		boolean isStale    = hasSummary
				&& row.contentEditedAt() != null
				&& row.contentEditedAt().isAfter(row.generatedAt());
		return new CodexChapterInfo(
				row.chapterId(),
				row.seq(),
				row.chapterNumber(),
				row.title(),
				bookId,
				bookTitle,
				hasSummary,
				isStale,
				row.provider());
	}

	// =========================================================================
	// Prompt building helpers
	// =========================================================================

	/**
	 * Builds a human-readable description of the schema fields that the AI
	 * should fill in. Includes field key, label, type, valid options (for
	 * SELECT), and help text.
	 */
	private static String buildSchemaDescription(List<CodexField> fields) {
		if (fields == null || fields.isEmpty()) {
			return "(no structured fields — provide a body description only)";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("Structured fields to fill in (use the exact field key names in your JSON response):\n");
		for (CodexField field : fields) {
			sb.append("- ").append(field.getKey())
					.append(" (").append(field.getLabel() != null ? field.getLabel() : field.getKey())
					.append("): ").append(field.getType());
			if ("SELECT".equals(field.getType())
					&& field.getOptions() != null && !field.getOptions().isEmpty()) {
				sb.append(" — valid options: ").append(String.join(", ", field.getOptions()));
			}
			if (field.getHelp() != null && !field.getHelp().isBlank()) {
				sb.append(" — ").append(field.getHelp().strip());
			}
			sb.append("\n");
		}
		return sb.toString().strip();
	}

	/**
	 * Formats the entry's currently-stored field values as a human-readable
	 * block so the AI knows what the author has already written and must not
	 * contradict.
	 */
	private static String buildExistingFieldsText(List<CodexField> fields, String structuredJson) {
		if (fields == null || fields.isEmpty()
				|| structuredJson == null || structuredJson.isBlank()) {
			return "none";
		}
		Map<String, Object> data;
		try {
			data = MAPPER.readValue(structuredJson, new TypeReference<Map<String, Object>>() {
			});
		} catch (Exception e) {
			return "none";
		}
		StringBuilder sb = new StringBuilder();
		for (CodexField field : fields) {
			Object val = data.get(field.getKey());
			if (val == null) continue;
			String text = val.toString().trim();
			if (text.isEmpty() || "_removedFields".equals(field.getKey())) continue;
			String label = field.getLabel() != null ? field.getLabel() : field.getKey();
			sb.append(label).append(": ").append(text).append("\n");
		}
		String result = sb.toString().strip();
		return result.isEmpty() ? "none" : result;
	}

	// =========================================================================
	// Utility helpers
	// =========================================================================

	private AiCredential resolveCredential(UUID userId, UUID credentialId) throws SQLException {
		if (credentialId != null) {
			return credentialDao.findById(credentialId, userId)
					.orElseThrow(() -> new ReviewException(Status.CONFLICT,
							"credential_not_found", "The selected AI credential was not found."));
		}
		return credentialDao.findDefault(userId)
				.orElseThrow(() -> new ReviewException(Status.CONFLICT,
						"no_ai_credential", "No AI provider key is configured. Add one in Settings."));
	}

	private static String htmlToPlainText(String html) {
		if (html == null || html.isBlank()) return "";
		Document      doc = Jsoup.parse(html);
		StringBuilder sb  = new StringBuilder();
		for (Element el : doc.body().select("p, h1, h2, h3, h4, blockquote, li")) {
			String text = el.text().trim();
			if (!text.isEmpty()) sb.append(text).append("\n\n");
		}
		String result = sb.toString().trim();
		return result.isEmpty() ? doc.text().trim() : result;
	}

	private static String firstNonBlank(String... values) {
		for (String v : values) {
			if (v != null && !v.isBlank()) return v.trim();
		}
		return "";
	}

	private static String blankToNull(String value) {
		if (value == null) return null;
		String t = value.trim();
		return t.isEmpty() ? null : t;
	}
}
