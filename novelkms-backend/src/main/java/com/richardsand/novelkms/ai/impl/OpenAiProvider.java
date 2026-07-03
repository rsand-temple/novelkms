package com.richardsand.novelkms.ai.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.richardsand.novelkms.ai.AiProvider;
import com.richardsand.novelkms.ai.AiProviderException;
import com.richardsand.novelkms.ai.BookSummaryRequest;
import com.richardsand.novelkms.ai.EditorialRequest;
import com.richardsand.novelkms.ai.EditorialResult;
import com.richardsand.novelkms.ai.MemoryRequest;
import com.richardsand.novelkms.ai.MemoryResult;
import com.richardsand.novelkms.ai.ReviewRequest;
import com.richardsand.novelkms.ai.ReviewResult;
import com.richardsand.novelkms.ai.SummaryRequest;
import com.richardsand.novelkms.ai.SummaryResult;
import com.richardsand.novelkms.ai.WeatherInterpretationRequest;
import com.richardsand.novelkms.ai.WeatherInterpretationResult;

/**
 * OpenAI implementation of {@link AiProvider}, calling the Chat Completions API
 * directly over {@link java.net.http.HttpClient} (no SDK dependency, consistent
 * with NovelKMS's hand-rolled-integration ethos).
 *
 * <p>Design choices:
 * <ul>
 *   <li>Uses {@code response_format: {"type":"json_object"}} rather than a strict
 *       {@code json_schema}. JSON-object mode is supported across a broad range
 *       of models, which matters for a BYOK tool where the user picks the model.</li>
 *   <li>Sends only {@code model}, {@code messages}, and {@code response_format} —
 *       no {@code temperature} or token caps — so requests don't 400 on reasoning
 *       models that reject those parameters.</li>
 *   <li>The review system prompt is assembled from two parts: a <b>form</b> block —
 *       the editorial persona/constraints, author-editable and supplied via
 *       {@link ReviewRequest#formInstructions()} — followed by a constant
 *       <b>functional</b> block that defines the JSON output contract
 *       NovelKMS consumes.</li>
 *   <li>The three free-text generation paths (chapter summary, book summary,
 *       editorial) each accept an optional {@code systemPrompt} field on their
 *       request record. When non-blank the provider uses it verbatim instead of
 *       the built-in constant, allowing the author-editable template cascade
 *       ({@code book → project → user global → system default}) to reach the
 *       provider. When the field is null or blank the built-in constant is used
 *       as a fallback (backward compatibility).</li>
 *   <li>Every generation call also accepts an optional one-time {@code userGuidance}
 *       string — a free-text author note for that single call only, unrelated to
 *       the persistent form/template override cascades. When present it is appended
 *       to the <em>user</em> message as a clearly-fenced addendum.</li>
 * </ul>
 */
public class OpenAiProvider implements AiProvider {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiProvider.class);

    public static final  String PROVIDER_KEY   = "OPENAI";
    public static final  String DEFAULT_MODEL  = "gpt-5.4";
    // chapter-review-v7: prompt-version constant (JSON output contract unchanged).
    public static final  String PROMPT_VERSION = "chapter-review-v7";
    /** Memory-document generation prompt version (free-text output; no JSON contract). */
    public static final  String MEMORY_PROMPT_VERSION = "memory-v2";
    /** Chapter-summary generation prompt version (free-text paragraph; no JSON contract). */
    public static final  String CHAPTER_SUMMARY_PROMPT_VERSION = "chapter-summary-v2";
    /** Book-summary generation prompt version (free-text synopsis built from chapter summaries). */
    public static final  String BOOK_SUMMARY_PROMPT_VERSION = "book-summary-v2";
    /** Editorial generation prompt version (free-text half-page editorial reading; no JSON contract). */
    public static final  String EDITORIAL_PROMPT_VERSION = "chapter-editorial-v1";
    /** Weather interpretation prompt version; facts are supplied by a weather provider. */
    public static final  String WEATHER_INTERPRETATION_PROMPT_VERSION = "weather-interpretation-v1";

    private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Override
    public String providerKey() {
        return PROVIDER_KEY;
    }

    @Override
    public String defaultModel() {
        return DEFAULT_MODEL;
    }

    @Override
    public ReviewResult review(ReviewRequest request) throws AiProviderException {
        String model = (request.model() == null || request.model().isBlank())
                ? DEFAULT_MODEL : request.model().trim();

        logger.info("OpenAI review request started: model={}, scope={}, unitLabel={}, promptVersion={}",
                model, scopeWord(request), safeLabel(request.unitLabel()), PROMPT_VERSION);
        logger.debug("OpenAI review request context: textChars={}, priorContextChars={}, referenceContextChars={}, userGuidanceChars={}, categoryCount={}",
                lengthOf(request.text()), lengthOf(request.priorContext()), lengthOf(request.referenceContext()),
                lengthOf(request.userGuidance()),
                request.categories() == null ? 0 : request.categories().size());

        String body = buildRequestBody(model, request);
        String content = postForContent(request.apiKey(), body);
        List<ReviewResult.Recommendation> recs = parseRecommendations(content);
        logger.info("OpenAI review request completed: model={}, recommendations={}", model, recs.size());
        return new ReviewResult(recs, content, PROMPT_VERSION);
    }

    @Override
    public MemoryResult generateMemory(MemoryRequest request) throws AiProviderException {
        String model = (request.model() == null || request.model().isBlank())
                ? DEFAULT_MODEL : request.model().trim();

        logger.info("OpenAI memory-generation request started: model={}, chapterLabel={}, promptVersion={}",
                model, safeLabel(request.chapterLabel()), MEMORY_PROMPT_VERSION);
        logger.debug("OpenAI memory-generation request context: chapterTextChars={}, templateChars={}, userGuidanceChars={}",
                lengthOf(request.chapterText()), lengthOf(request.template()), lengthOf(request.userGuidance()));

        String body = buildMemoryRequestBody(model, request);
        String content = postForContent(request.apiKey(), body);
        logger.info("OpenAI memory-generation request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new MemoryResult(content.strip(), MEMORY_PROMPT_VERSION);
    }

    @Override
    public SummaryResult generateChapterSummary(SummaryRequest request) throws AiProviderException {
        String model = (request.model() == null || request.model().isBlank())
                ? DEFAULT_MODEL : request.model().trim();

        logger.info("OpenAI chapter-summary request started: model={}, chapterLabel={}, promptVersion={}",
                model, safeLabel(request.chapterLabel()), CHAPTER_SUMMARY_PROMPT_VERSION);
        logger.debug("OpenAI chapter-summary request context: chapterTextChars={}, userGuidanceChars={}, hasCustomPrompt={}",
                lengthOf(request.chapterText()), lengthOf(request.userGuidance()),
                request.systemPrompt() != null && !request.systemPrompt().isBlank());

        String body = buildChapterSummaryRequestBody(model, request);
        String content = postForContent(request.apiKey(), body);
        logger.info("OpenAI chapter-summary request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new SummaryResult(content.strip(), CHAPTER_SUMMARY_PROMPT_VERSION);
    }

    @Override
    public SummaryResult generateBookSummary(BookSummaryRequest request) throws AiProviderException {
        String model = (request.model() == null || request.model().isBlank())
                ? DEFAULT_MODEL : request.model().trim();

        logger.info("OpenAI book-summary request started: model={}, bookTitle={}, maxWords={}, promptVersion={}",
                model, safeLabel(request.bookTitle()), request.maxWords(), BOOK_SUMMARY_PROMPT_VERSION);
        logger.debug("OpenAI book-summary request context: chapterSummariesChars={}, userGuidanceChars={}, hasCustomPrompt={}",
                lengthOf(request.chapterSummaries()), lengthOf(request.userGuidance()),
                request.systemPrompt() != null && !request.systemPrompt().isBlank());

        String body = buildBookSummaryRequestBody(model, request);
        String content = postForContent(request.apiKey(), body);
        logger.info("OpenAI book-summary request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new SummaryResult(content.strip(), BOOK_SUMMARY_PROMPT_VERSION);
    }

    @Override
    public EditorialResult generateEditorial(EditorialRequest request) throws AiProviderException {
        String model = (request.model() == null || request.model().isBlank())
                ? DEFAULT_MODEL : request.model().trim();

        logger.info("OpenAI editorial request started: model={}, chapterLabel={}, promptVersion={}",
                model, safeLabel(request.chapterLabel()), EDITORIAL_PROMPT_VERSION);
        logger.debug("OpenAI editorial request context: chapterTextChars={}, priorContextChars={}, referenceContextChars={}, userGuidanceChars={}, hasCustomPrompt={}",
                lengthOf(request.chapterText()), lengthOf(request.priorContext()),
                lengthOf(request.referenceContext()), lengthOf(request.userGuidance()),
                request.systemPrompt() != null && !request.systemPrompt().isBlank());

        String body = buildEditorialRequestBody(model, request);
        String content = postForContent(request.apiKey(), body);
        logger.info("OpenAI editorial request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new EditorialResult(content.strip(), EDITORIAL_PROMPT_VERSION);
    }

    @Override
    public WeatherInterpretationResult interpretWeather(WeatherInterpretationRequest request) throws AiProviderException {
        String model = (request.model() == null || request.model().isBlank())
                ? DEFAULT_MODEL : request.model().trim();

        logger.info("OpenAI weather-interpretation request started: model={}, promptVersion={}",
                model, WEATHER_INTERPRETATION_PROMPT_VERSION);
        logger.debug("OpenAI weather-interpretation context: weatherFactsChars={}, sceneContextChars={}",
                lengthOf(request.weatherFacts()), lengthOf(request.sceneContext()));

        String body = buildWeatherInterpretationRequestBody(model, request);
        String content = postForContent(request.apiKey(), body);
        logger.info("OpenAI weather-interpretation request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new WeatherInterpretationResult(content.strip(), WEATHER_INTERPRETATION_PROMPT_VERSION);
    }

    /**
     * Sends a Chat Completions request and returns the assistant message content,
     * translating transport and non-200 responses into {@link AiProviderException}.
     */
    private String postForContent(String apiKey, String body) throws AiProviderException {
        logger.debug("OpenAI HTTP request prepared: endpoint={}, requestBytes={}",
                ENDPOINT, body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            logger.warn("OpenAI HTTP request failed before response: {}", e.getMessage());
            throw new AiProviderException("Could not reach OpenAI: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("OpenAI HTTP request interrupted");
            throw new AiProviderException("OpenAI request was interrupted", e);
        }

        if (response.statusCode() != 200) {
            logger.warn("OpenAI HTTP request returned non-success status: status={}, responseChars={}",
                    response.statusCode(), lengthOf(response.body()));
            throw new AiProviderException(extractErrorMessage(response.statusCode(), response.body()));
        }
        logger.debug("OpenAI HTTP request succeeded: status={}, responseChars={}",
                response.statusCode(), lengthOf(response.body()));
        return extractContent(response.body());
    }

    private String buildRequestBody(String model, ReviewRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);

        ArrayNode messages = root.putArray("messages");

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", systemPrompt(scopeWord(request), request.categories(), request.formInstructions()));

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userPrompt(request));

        ObjectNode responseFormat = root.putObject("response_format");
        responseFormat.put("type", "json_object");

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build OpenAI request body", e);
        }
    }

    /**
     * Assembles the review system prompt as {@code form + "\n\n" + functional}.
     *
     * <p>{@code form} is the author-supplied editorial persona/constraints (or
     * the system default, resolved upstream). {@code functional} is the constant
     * JSON output contract NovelKMS consumes.
     */
    private String systemPrompt(String unit, List<String> categories, String formInstructions) {
        String form = (formInstructions == null || formInstructions.isBlank())
                ? FORM_FALLBACK
                : formInstructions.strip();
        return form + "\n\n" + functionalBlock(unit, categories);
    }

    /**
     * Minimal safety net only. The real default text lives in
     * {@code AiFormInstructionsDefaults} and is always resolved upstream.
     */
    private static final String FORM_FALLBACK =
            "You are an experienced editor reviewing a section of a novel. You do not "
            + "rewrite the manuscript; you produce specific, actionable editorial notes.";

    private String functionalBlock(String unit, List<String> categories) {
        String categoryList = (categories == null || categories.isEmpty())
                ? "Continuity, Characterization, Pacing, Dialogue, Clarity, Grammar, General Notes"
                : String.join(", ", categories);
        return """
                Return your review as a single JSON object and nothing else — no prose, no \
                Markdown, no code fences — in exactly this shape:
                {"recommendations":[{"category":"...","severity":"LOW|MEDIUM|HIGH",\
                "location":"where in the %1$s this applies","recommendation":"the note",\
                "codexCategory":"CANON","codexTitle":"short entry title",\
                "anchorText":"verbatim quote from the %1$s"}]}

                Field requirements for every recommendation:
                - category: exactly one of: %2$s.
                - severity: exactly one of LOW, MEDIUM, or HIGH.
                - location: a brief description of where in the %1$s the note applies.
                - recommendation: the editorial note itself.
                - codexCategory: exactly one of CHARACTER, VOICE, PLOT, WORLD, TIMELINE, CANON, NOTES — \
                  a suggestion for how the author could file this note in the project's knowledge base \
                  (the "codex") in one click. Use CANON for established facts, rules, or continuity \
                  points to lock in; CHARACTER for character facts/arcs; VOICE for how a character \
                  speaks; WORLD for setting, institutions, or objects; TIMELINE for dates and ordering; \
                  PLOT for plot threads; NOTES for anything else.
                - codexTitle: a short (3-8 word) title for that codex entry.
                - anchorText: a short verbatim quote (5-30 words) copied exactly, word-for-word, from \
                  the %1$s text, identifying the passage the recommendation refers to. It is used to \
                  scroll the author's editor to that passage, so it must appear word-for-word in the %1$s.

                If you have no substantive notes, return {"recommendations":[]}.
                """.formatted(unit, categoryList);
    }

    /** Wrapper instruction prepended to the memory template for generation. */
    private static final String MEMORY_WRAPPER = """
            You are summarizing one chapter of a novel to build a running memory \
            document used as continuity context for later editorial review. Read the \
            chapter text and fill in the template below for this chapter. Output only \
            the filled-in template, following its headings and structure exactly. Base \
            every statement strictly on what is present in the chapter text — do not \
            invent, infer beyond the text, or speculate. Be concise and factual. Where \
            the template shows a chapter placeholder, use the chapter's label.""";

    private String buildMemoryRequestBody(String model, MemoryRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);

        ArrayNode messages = root.putArray("messages");

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", MEMORY_WRAPPER + "\n\nTemplate:\n\n" + nullToBlank(request.template()));

        StringBuilder user = new StringBuilder();
        user.append("Chapter: ").append(nullToBlank(request.chapterLabel())).append("\n\n");
        if (request.userGuidance() != null && !request.userGuidance().isBlank()) {
            user.append("Additional guidance from the author for this generation only — follow it, but it ")
                .append("is not material to summarize:\n\n")
                .append(request.userGuidance().strip()).append("\n\n")
                .append("----------------------------------------\n\n");
        }
        user.append("Chapter text:\n\n").append(nullToBlank(request.chapterText()));

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", user.toString());

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build OpenAI memory request body", e);
        }
    }

    /**
     * Built-in fallback for chapter-summary generation. Used only when no
     * author-provided template is active (i.e. {@code request.systemPrompt()} is
     * null or blank). When the template cascade resolves to a user/project/book
     * override or the system-default constant from
     * {@code ChapterSummaryTemplateDefaults}, that text is passed in
     * {@code request.systemPrompt()} and used verbatim instead.
     */
    private static final String CHAPTER_SUMMARY_WRAPPER = """
            You are summarizing one chapter of a novel. Write a single, clear, \
            human-readable paragraph that captures what happens in the chapter: the \
            key events, the characters involved, and how the chapter moves the story \
            forward. Write in flowing prose — no headings, no bullet points, no lists, \
            no preamble or labels. Base every statement strictly on the chapter text; \
            do not invent or speculate. Output only the summary paragraph.""";

    private String buildChapterSummaryRequestBody(String model, SummaryRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);

        ArrayNode messages = root.putArray("messages");

        // Use the author-provided template from the resolution cascade when set;
        // fall back to the built-in constant otherwise.
        String systemContent = (request.systemPrompt() != null && !request.systemPrompt().isBlank())
                ? request.systemPrompt()
                : CHAPTER_SUMMARY_WRAPPER;

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", systemContent);

        StringBuilder user = new StringBuilder();
        user.append("Chapter: ").append(nullToBlank(request.chapterLabel())).append("\n\n");
        if (request.userGuidance() != null && !request.userGuidance().isBlank()) {
            user.append("Additional guidance from the author for this generation only — follow it, but it ")
                .append("is not material to summarize:\n\n")
                .append(request.userGuidance().strip()).append("\n\n")
                .append("----------------------------------------\n\n");
        }
        user.append("Chapter text:\n\n").append(nullToBlank(request.chapterText()));

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", user.toString());

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build OpenAI chapter-summary request body", e);
        }
    }

    /**
     * Built-in fallback for book-summary generation. Used only when no
     * author-provided template is active. {@code %1$d} is the word ceiling and
     * is substituted only for this fallback; author-provided prompts state their
     * own limit in plain text and are used verbatim.
     */
    private static final String BOOK_SUMMARY_WRAPPER = """
            You are writing a synopsis of an entire novel from the per-chapter \
            summaries provided below, which are given in reading order. Synthesize \
            them into one cohesive, human-readable overview of the whole book — the \
            overall arc, the principal characters, and how the story resolves. Write \
            in flowing prose with no headings, bullet points, or lists, and no \
            preamble or labels. Use no more than %1$d words; do not exceed this \
            limit. Base everything strictly on the supplied chapter summaries; do not \
            invent material not present in them. Output only the synopsis.""";

    private String buildBookSummaryRequestBody(String model, BookSummaryRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);

        ArrayNode messages = root.putArray("messages");

        int maxWords = request.maxWords() > 0 ? request.maxWords() : 1000;

        // Use the author-provided template when set; fall back to the built-in
        // constant (with maxWords substitution) otherwise.
        String systemContent = (request.systemPrompt() != null && !request.systemPrompt().isBlank())
                ? request.systemPrompt()
                : BOOK_SUMMARY_WRAPPER.formatted(maxWords);

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", systemContent);

        StringBuilder user = new StringBuilder();
        if (request.bookTitle() != null && !request.bookTitle().isBlank()) {
            user.append("Book: ").append(request.bookTitle().trim()).append("\n\n");
        }
        if (request.userGuidance() != null && !request.userGuidance().isBlank()) {
            user.append("Additional guidance from the author for this generation only — follow it, but it ")
                .append("is not material to summarize:\n\n")
                .append(request.userGuidance().strip()).append("\n\n")
                .append("----------------------------------------\n\n");
        }
        user.append("Chapter summaries (in reading order):\n\n")
            .append(nullToBlank(request.chapterSummaries()));

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", user.toString());

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build OpenAI book-summary request body", e);
        }
    }

    /**
     * Built-in fallback for editorial generation. Used only when no
     * author-provided template is active.
     */
    private static final String EDITORIAL_WRAPPER = """
            You are an experienced developmental editor giving the author your \
            overall editorial impression of a single chapter of their novel. Read \
            the chapter and share what you think of it as a whole: its tone and \
            mood, whether the genre feels consistent or is drifting, how the \
            characters and their arcs are developing, and how the storyline is \
            evolving. Where prior-chapter context is provided, judge continuity \
            and momentum against it. Write in warm, direct prose addressed to the \
            author — a few short paragraphs at most, roughly half a page; less is \
            more. Do NOT produce a list of findings, and do NOT point out \
            spelling, grammar, punctuation, or other line-level issues unless \
            something is truly egregious — those belong to a separate review. Do \
            not restate the plot back as a summary; give your editorial read on \
            it. Output only the editorial.""";

    private String buildEditorialRequestBody(String model, EditorialRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);

        ArrayNode messages = root.putArray("messages");

        // Use the author-provided template when set; fall back to the built-in constant.
        String systemContent = (request.systemPrompt() != null && !request.systemPrompt().isBlank())
                ? request.systemPrompt()
                : EDITORIAL_WRAPPER;

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", systemContent);

        StringBuilder user = new StringBuilder();

        // Optional context blocks, clearly fenced off as background — not the
        // material under editorial consideration.
        if (request.referenceContext() != null && !request.referenceContext().isBlank()) {
            user.append("Reference material — established canon and voice the manuscript must respect. ")
                .append("Entries may list structured canonical fields (labeled) and a description. ")
                .append("Use it to judge the chapter, but do not comment on it directly:\n\n")
                .append(request.referenceContext().strip()).append("\n\n")
                .append("----------------------------------------\n\n");
        }
        if (request.priorContext() != null && !request.priorContext().isBlank()) {
            user.append("Story so far — memory documents of the preceding chapters, for continuity ")
                .append("context only. Do not comment on these summaries:\n\n")
                .append(request.priorContext().strip()).append("\n\n")
                .append("----------------------------------------\n\n");
        }
        if (request.userGuidance() != null && !request.userGuidance().isBlank()) {
            user.append("Additional guidance from the author for this editorial only — follow it, but it ")
                .append("is not material to comment on:\n\n")
                .append(request.userGuidance().strip()).append("\n\n")
                .append("----------------------------------------\n\n");
        }

        user.append("Chapter: ").append(nullToBlank(request.chapterLabel()));
        if (request.subtitle() != null && !request.subtitle().isBlank()) {
            user.append(" — ").append(request.subtitle().trim());
        }
        user.append("\n\nChapter text:\n\n").append(nullToBlank(request.chapterText()));

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", user.toString());

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build OpenAI editorial request body", e);
        }
    }

    private String userPrompt(ReviewRequest request) {
        String unit    = scopeWord(request);
        String heading = capitalize(unit);
        StringBuilder sb = new StringBuilder();

        if (request.referenceContext() != null && !request.referenceContext().isBlank()) {
            sb.append("Reference material — established canon and voice the manuscript must respect. ")
              .append("Entries may list structured canonical fields (labeled) and a description. ")
              .append("Use it to judge the ").append(unit).append(", but do not review it:\n\n")
              .append(request.referenceContext().strip()).append("\n\n")
              .append("----------------------------------------\n\n");
        }
        if (request.priorContext() != null && !request.priorContext().isBlank()) {
            sb.append("Story so far — memory documents of the preceding chapters, for continuity ")
              .append("context only. Do not review these summaries:\n\n")
              .append(request.priorContext().strip()).append("\n\n")
              .append("----------------------------------------\n\n");
            sb.append("Review the following ").append(unit).append(".\n\n");
        }
        if (request.userGuidance() != null && !request.userGuidance().isBlank()) {
            sb.append("Additional guidance from the author for this review only — follow it, but it is ")
              .append("not material to review:\n\n")
              .append(request.userGuidance().strip()).append("\n\n")
              .append("----------------------------------------\n\n");
        }

        sb.append(heading).append(": ").append(nullToBlank(request.unitLabel()));
        if (request.subtitle() != null && !request.subtitle().isBlank()) {
            sb.append(" — ").append(request.subtitle().trim());
        }
        sb.append("\n\n");
        sb.append(heading).append(" text:\n\n");
        sb.append(nullToBlank(request.text()));
        return sb.toString();
    }

    private String buildWeatherInterpretationRequestBody(String model, WeatherInterpretationRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);

        ArrayNode messages = root.putArray("messages");

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", """
                You are helping an author interpret supplied weather data for a fiction scene.

                Rules:
                - Do not invent meteorological facts not present in the supplied weather data.
                - Do not claim the weather is an observed station record unless the supplied source says so.
                - If the source is modeled, reanalysis, or forecast data, mention uncertainty naturally.
                - Focus on what the weather would likely feel like to a person in the scene.
                - Keep the answer practical for writing: clothing, light, footing, mood, visibility,
                  and plausible sensory details.
                - Do not rewrite manuscript prose.
                """);

        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", weatherInterpretationPrompt(request));

        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build OpenAI weather interpretation request body", e);
        }
    }

    private String weatherInterpretationPrompt(WeatherInterpretationRequest request) {
        String sceneContext = request.sceneContext() == null || request.sceneContext().isBlank()
                ? "No scene context supplied."
                : request.sceneContext().strip();

        return """
                Weather data:
                %s

                Optional scene context:
                %s
                """.formatted(request.weatherFacts(), sceneContext);
    }

    private String extractContent(String responseBody) throws AiProviderException {
        try {
            JsonNode root    = mapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new AiProviderException("OpenAI returned no choices");
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new AiProviderException("OpenAI returned an empty response");
            }
            return content;
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Could not read OpenAI response: " + e.getMessage(), e);
        }
    }

    private List<ReviewResult.Recommendation> parseRecommendations(String content) throws AiProviderException {
        String json = stripCodeFences(content);
        try {
            logger.debug("Parsing OpenAI review JSON: contentChars={}, jsonChars={}", lengthOf(content), lengthOf(json));
            JsonNode root  = mapper.readTree(json);
            JsonNode array = root.path("recommendations");
            if (!array.isArray()) {
                throw new AiProviderException("OpenAI response did not contain a recommendations array");
            }
            List<ReviewResult.Recommendation> result = new ArrayList<>();
            for (JsonNode node : array) {
                String category       = textOrNull(node, "category");
                String severity       = textOrNull(node, "severity");
                String location       = textOrNull(node, "location");
                String recommendation = textOrNull(node, "recommendation");
                String codexCategory  = textOrNull(node, "codexCategory");
                String codexTitle     = textOrNull(node, "codexTitle");
                String anchorText     = textOrNull(node, "anchorText");
                if (recommendation == null || recommendation.isBlank()) continue;
                result.add(new ReviewResult.Recommendation(
                        category, severity, location, recommendation,
                        codexCategory, codexTitle, anchorText));
            }
            logger.debug("Parsed OpenAI review JSON: recommendations={}", result.size());
            return result;
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Failed to parse OpenAI recommendations JSON", e);
            throw new AiProviderException("OpenAI returned a response that could not be parsed as review JSON");
        }
    }

    private String extractErrorMessage(int status, String body) {
        try {
            JsonNode error   = mapper.readTree(body).path("error");
            String   message = error.path("message").asText("");
            if (!message.isBlank()) {
                return "OpenAI error (HTTP " + status + "): " + message;
            }
        } catch (Exception ignored) {
            // fall through to a generic message
        }
        return "OpenAI request failed (HTTP " + status + ")";
    }

    private static String scopeWord(ReviewRequest request) {
        String word = request.scopeWord();
        return (word == null || word.isBlank()) ? "chapter" : word.trim().toLowerCase();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String stripCodeFences(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) trimmed = trimmed.substring(firstNewline + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private static String safeLabel(String value) {
        if (value == null || value.isBlank()) return "";
        String stripped = value.strip().replaceAll("\\s+", " ");
        return stripped.length() <= 120 ? stripped : stripped.substring(0, 120) + "...";
    }
}
