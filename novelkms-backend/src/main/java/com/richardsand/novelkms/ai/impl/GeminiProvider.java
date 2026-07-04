package com.richardsand.novelkms.ai.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.richardsand.novelkms.ai.AiProvider;
import com.richardsand.novelkms.ai.AiProviderException;
import com.richardsand.novelkms.ai.BookSummaryRequest;
import com.richardsand.novelkms.ai.CodexFillRequest;
import com.richardsand.novelkms.ai.CodexFillResult;
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
 * Google Gemini implementation of {@link AiProvider}, calling the Gemini
 * generateContent REST API directly over {@link java.net.http.HttpClient}
 * (no SDK dependency, consistent with NovelKMS's hand-rolled-integration ethos).
 *
 * <p>
 * Design notes vs. {@link OpenAiProvider} and {@link AnthropicProvider}:
 * <ul>
 * <li>Auth uses an API key supplied as a {@code ?key=} URL query parameter
 * rather than a request header. The model is embedded in the URL path:
 * {@code .../models/{model}:generateContent?key={apiKey}}.</li>
 * <li>The system prompt goes in a top-level {@code "systemInstruction"} object
 * with a {@code "parts"} array, rather than as a header message or a
 * top-level {@code "system"} string.</li>
 * <li>User messages go in a {@code "contents"} array where each element has
 * a {@code "role"} and a {@code "parts"} array of text objects.</li>
 * <li>Token ceilings are set via {@code "generationConfig.maxOutputTokens"}.
 * For JSON calls (review and codex fill), {@code "responseMimeType":"application/json"}
 * is also included in {@code generationConfig} — Gemini's equivalent of
 * OpenAI's {@code response_format: json_object}. Free-text calls omit it.</li>
 * <li>Response text is extracted from
 * {@code candidates[0].content.parts[0].text}.</li>
 * <li>Prompt version constants are identical to those in {@link OpenAiProvider}
 * because the prompt content is the same — only the transport format differs.</li>
 * <li>{@code functionalBlock()} is duplicated self-contained per the established
 * convention (provider classes are self-contained).</li>
 * </ul>
 */
public class GeminiProvider implements AiProvider {
    private static final Logger logger = LoggerFactory.getLogger(GeminiProvider.class);

    public static final String PROVIDER_KEY  = "GEMINI";
    public static final String DEFAULT_MODEL = "gemini-2.5-flash";

    /** Reuse the same prompt-version labels; the prompt content is provider-agnostic. */
    public static final String PROMPT_VERSION                 = OpenAiProvider.PROMPT_VERSION;
    public static final String MEMORY_PROMPT_VERSION          = OpenAiProvider.MEMORY_PROMPT_VERSION;
    public static final String CHAPTER_SUMMARY_PROMPT_VERSION = OpenAiProvider.CHAPTER_SUMMARY_PROMPT_VERSION;
    public static final String BOOK_SUMMARY_PROMPT_VERSION    = OpenAiProvider.BOOK_SUMMARY_PROMPT_VERSION;
    public static final String EDITORIAL_PROMPT_VERSION       = OpenAiProvider.EDITORIAL_PROMPT_VERSION;
    public static final String CODEX_FILL_PROMPT_VERSION      = OpenAiProvider.CODEX_FILL_PROMPT_VERSION;
    public static final String WEATHER_INTERPRETATION_VERSION = OpenAiProvider.WEATHER_INTERPRETATION_PROMPT_VERSION;

    private static final String BASE_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";

    /**
     * maxOutputTokens ceilings per call type. Gemini stops naturally at
     * end-of-output well before these limits in practice.
     */
    private static final int MAX_TOKENS_REVIEW      = 4096;
    private static final int MAX_TOKENS_MEMORY      = 2048;
    private static final int MAX_TOKENS_SUMMARY     = 2048;
    private static final int MAX_TOKENS_EDITORIAL   = 2048;
    private static final int MAX_TOKENS_CODEX_FILL  = 2048;
    private static final int MAX_TOKENS_WEATHER     = 1024;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient   client = HttpClient.newBuilder()
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

    // -------------------------------------------------------------------------
    // AiProvider interface
    // -------------------------------------------------------------------------

    @Override
    public ReviewResult review(ReviewRequest request) throws AiProviderException {
        String model = resolveModel(request.model(), DEFAULT_MODEL);

        logger.info("Gemini review request started: model={}, scope={}, unitLabel={}, promptVersion={}",
                model, scopeWord(request), safeLabel(request.unitLabel()), PROMPT_VERSION);
        logger.debug("Gemini review request context: textChars={}, priorContextChars={}, referenceContextChars={}, userGuidanceChars={}, categoryCount={}",
                lengthOf(request.text()), lengthOf(request.priorContext()), lengthOf(request.referenceContext()),
                lengthOf(request.userGuidance()),
                request.categories() == null ? 0 : request.categories().size());

        String                            body    = buildReviewRequestBody(model, request);
        String                            content = postForContent(request.apiKey(), model, body);
        List<ReviewResult.Recommendation> recs    = parseRecommendations(content);
        logger.info("Gemini review request completed: model={}, recommendations={}", model, recs.size());
        return new ReviewResult(recs, content, PROMPT_VERSION);
    }

    @Override
    public MemoryResult generateMemory(MemoryRequest request) throws AiProviderException {
        String model = resolveModel(request.model(), DEFAULT_MODEL);

        logger.info("Gemini memory-generation request started: model={}, chapterLabel={}, promptVersion={}",
                model, safeLabel(request.chapterLabel()), MEMORY_PROMPT_VERSION);
        logger.debug("Gemini memory-generation request context: chapterTextChars={}, templateChars={}, userGuidanceChars={}",
                lengthOf(request.chapterText()), lengthOf(request.template()), lengthOf(request.userGuidance()));

        String body    = buildMemoryRequestBody(model, request);
        String content = postForContent(request.apiKey(), model, body);
        logger.info("Gemini memory-generation request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new MemoryResult(content.strip(), MEMORY_PROMPT_VERSION);
    }

    @Override
    public SummaryResult generateChapterSummary(SummaryRequest request) throws AiProviderException {
        String model = resolveModel(request.model(), DEFAULT_MODEL);

        logger.info("Gemini chapter-summary request started: model={}, chapterLabel={}, promptVersion={}",
                model, safeLabel(request.chapterLabel()), CHAPTER_SUMMARY_PROMPT_VERSION);
        logger.debug("Gemini chapter-summary request context: chapterTextChars={}, userGuidanceChars={}, hasCustomPrompt={}",
                lengthOf(request.chapterText()), lengthOf(request.userGuidance()),
                request.systemPrompt() != null && !request.systemPrompt().isBlank());

        String body    = buildChapterSummaryRequestBody(model, request);
        String content = postForContent(request.apiKey(), model, body);
        logger.info("Gemini chapter-summary request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new SummaryResult(content.strip(), CHAPTER_SUMMARY_PROMPT_VERSION);
    }

    @Override
    public SummaryResult generateBookSummary(BookSummaryRequest request) throws AiProviderException {
        String model = resolveModel(request.model(), DEFAULT_MODEL);

        logger.info("Gemini book-summary request started: model={}, bookTitle={}, maxWords={}, promptVersion={}",
                model, safeLabel(request.bookTitle()), request.maxWords(), BOOK_SUMMARY_PROMPT_VERSION);
        logger.debug("Gemini book-summary request context: chapterSummariesChars={}, userGuidanceChars={}, hasCustomPrompt={}",
                lengthOf(request.chapterSummaries()), lengthOf(request.userGuidance()),
                request.systemPrompt() != null && !request.systemPrompt().isBlank());

        String body    = buildBookSummaryRequestBody(model, request);
        String content = postForContent(request.apiKey(), model, body);
        logger.info("Gemini book-summary request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new SummaryResult(content.strip(), BOOK_SUMMARY_PROMPT_VERSION);
    }

    @Override
    public EditorialResult generateEditorial(EditorialRequest request) throws AiProviderException {
        String model = resolveModel(request.model(), DEFAULT_MODEL);

        logger.info("Gemini editorial request started: model={}, chapterLabel={}, promptVersion={}",
                model, safeLabel(request.chapterLabel()), EDITORIAL_PROMPT_VERSION);
        logger.debug("Gemini editorial request context: chapterTextChars={}, priorContextChars={}, referenceContextChars={}, userGuidanceChars={}, hasCustomPrompt={}",
                lengthOf(request.chapterText()), lengthOf(request.priorContext()),
                lengthOf(request.referenceContext()), lengthOf(request.userGuidance()),
                request.systemPrompt() != null && !request.systemPrompt().isBlank());

        String body    = buildEditorialRequestBody(model, request);
        String content = postForContent(request.apiKey(), model, body);
        logger.info("Gemini editorial request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new EditorialResult(content.strip(), EDITORIAL_PROMPT_VERSION);
    }

    @Override
    public CodexFillResult fillCodexEntry(CodexFillRequest request) throws AiProviderException {
        String model = resolveModel(request.model(), DEFAULT_MODEL);

        logger.info("Gemini codex-fill request started: model={}, entryTitle={}, promptVersion={}",
                model, safeLabel(request.entryTitle()), CODEX_FILL_PROMPT_VERSION);
        logger.debug("Gemini codex-fill context: schemaDescChars={}, existingFieldsChars={}, manuscriptContextChars={}, referenceContextChars={}, userGuidanceChars={}",
                lengthOf(request.schemaDescription()), lengthOf(request.existingFields()),
                lengthOf(request.manuscriptContext()), lengthOf(request.referenceContext()),
                lengthOf(request.userGuidance()));

        String body    = buildCodexFillRequestBody(model, request);
        String content = postForContent(request.apiKey(), model, body);
        CodexFillResult result = parseCodexFillResult(content);
        logger.info("Gemini codex-fill request completed: model={}, fieldCount={}", model, result.fields().size());
        return result;
    }

    @Override
    public WeatherInterpretationResult interpretWeather(WeatherInterpretationRequest request) throws AiProviderException {
        String model = resolveModel(request.model(), DEFAULT_MODEL);

        logger.info("Gemini weather-interpretation request started: model={}, promptVersion={}",
                model, WEATHER_INTERPRETATION_VERSION);
        logger.debug("Gemini weather-interpretation context: weatherFactsChars={}, sceneContextChars={}",
                lengthOf(request.weatherFacts()), lengthOf(request.sceneContext()));

        String body    = buildWeatherRequestBody(model, request);
        String content = postForContent(request.apiKey(), model, body);
        logger.info("Gemini weather-interpretation request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new WeatherInterpretationResult(content.strip(), WEATHER_INTERPRETATION_VERSION);
    }

    // -------------------------------------------------------------------------
    // HTTP transport
    // -------------------------------------------------------------------------

    /**
     * Posts to the Gemini generateContent endpoint and returns the text from
     * {@code candidates[0].content.parts[0].text}, translating transport and
     * non-200 responses into {@link AiProviderException}.
     *
     * <p>
     * The Gemini API key is supplied as a {@code ?key=} URL query parameter
     * and the model is embedded in the URL path — both differ from the
     * header-based auth used by the other providers.
     */
    private String postForContent(String apiKey, String model, String body) throws AiProviderException {
        String url = BASE_ENDPOINT + model + ":generateContent?key=" + apiKey;

        logger.debug("Gemini HTTP request prepared: model={}, requestBytes={}",
                model, body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            logger.warn("Gemini HTTP request failed before response: {}", e.getMessage());
            throw new AiProviderException("Could not reach Gemini: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Gemini HTTP request interrupted");
            throw new AiProviderException("Gemini request was interrupted", e);
        }

        if (response.statusCode() != 200) {
            logger.warn("Gemini HTTP request returned non-success status: status={}, responseChars={}",
                    response.statusCode(), lengthOf(response.body()));
            throw new AiProviderException(extractErrorMessage(response.statusCode(), response.body()));
        }

        logger.debug("Gemini HTTP request succeeded: status={}, responseChars={}",
                response.statusCode(), lengthOf(response.body()));
        return extractContent(response.body());
    }

    // -------------------------------------------------------------------------
    // Request body builders
    // -------------------------------------------------------------------------

    /**
     * Review body. Includes {@code responseMimeType:"application/json"} in
     * {@code generationConfig} to enforce a pure-JSON response.
     */
    private String buildReviewRequestBody(String model, ReviewRequest request) {
        ObjectNode root = mapper.createObjectNode();

        String systemContent = reviewSystemPrompt(
                scopeWord(request), request.categories(), request.formInstructions());
        root.putObject("systemInstruction")
                .putArray("parts")
                .addObject().put("text", systemContent);

        ArrayNode  contents = root.putArray("contents");
        ObjectNode userTurn = contents.addObject();
        userTurn.put("role", "user");
        userTurn.putArray("parts").addObject().put("text", reviewUserPrompt(request));

        ObjectNode genConfig = root.putObject("generationConfig");
        genConfig.put("maxOutputTokens", MAX_TOKENS_REVIEW);
        genConfig.put("responseMimeType", "application/json");

        return serialize(root, "review");
    }

    private String buildMemoryRequestBody(String model, MemoryRequest request) {
        ObjectNode root = mapper.createObjectNode();

        root.putObject("systemInstruction")
                .putArray("parts")
                .addObject().put("text", MEMORY_WRAPPER + "\n\nTemplate:\n\n" + nullToBlank(request.template()));

        StringBuilder user = new StringBuilder();
        user.append("Chapter: ").append(nullToBlank(request.chapterLabel())).append("\n\n");
        appendGuidance(user, request.userGuidance(), "summarize");
        user.append("Chapter text:\n\n").append(nullToBlank(request.chapterText()));

        ArrayNode  contents = root.putArray("contents");
        ObjectNode userTurn = contents.addObject();
        userTurn.put("role", "user");
        userTurn.putArray("parts").addObject().put("text", user.toString());

        root.putObject("generationConfig").put("maxOutputTokens", MAX_TOKENS_MEMORY);

        return serialize(root, "memory");
    }

    private String buildChapterSummaryRequestBody(String model, SummaryRequest request) {
        ObjectNode root = mapper.createObjectNode();

        String systemContent = (request.systemPrompt() != null && !request.systemPrompt().isBlank())
                ? request.systemPrompt()
                : CHAPTER_SUMMARY_WRAPPER;
        root.putObject("systemInstruction")
                .putArray("parts")
                .addObject().put("text", systemContent);

        StringBuilder user = new StringBuilder();
        user.append("Chapter: ").append(nullToBlank(request.chapterLabel())).append("\n\n");
        appendGuidance(user, request.userGuidance(), "summarize");
        user.append("Chapter text:\n\n").append(nullToBlank(request.chapterText()));

        ArrayNode  contents = root.putArray("contents");
        ObjectNode userTurn = contents.addObject();
        userTurn.put("role", "user");
        userTurn.putArray("parts").addObject().put("text", user.toString());

        root.putObject("generationConfig").put("maxOutputTokens", MAX_TOKENS_SUMMARY);

        return serialize(root, "chapter-summary");
    }

    private String buildBookSummaryRequestBody(String model, BookSummaryRequest request) {
        ObjectNode root = mapper.createObjectNode();

        String systemContent = (request.systemPrompt() != null && !request.systemPrompt().isBlank())
                ? request.systemPrompt()
                : BOOK_SUMMARY_WRAPPER.formatted(request.maxWords());
        root.putObject("systemInstruction")
                .putArray("parts")
                .addObject().put("text", systemContent);

        StringBuilder user = new StringBuilder();
        user.append("Book: ").append(nullToBlank(request.bookTitle())).append("\n\n");
        appendGuidance(user, request.userGuidance(), "summarize");
        user.append("Chapter summaries:\n\n").append(nullToBlank(request.chapterSummaries()));

        ArrayNode  contents = root.putArray("contents");
        ObjectNode userTurn = contents.addObject();
        userTurn.put("role", "user");
        userTurn.putArray("parts").addObject().put("text", user.toString());

        root.putObject("generationConfig").put("maxOutputTokens", MAX_TOKENS_SUMMARY);

        return serialize(root, "book-summary");
    }

    private String buildEditorialRequestBody(String model, EditorialRequest request) {
        ObjectNode root = mapper.createObjectNode();

        String systemContent = (request.systemPrompt() != null && !request.systemPrompt().isBlank())
                ? request.systemPrompt()
                : EDITORIAL_WRAPPER;
        root.putObject("systemInstruction")
                .putArray("parts")
                .addObject().put("text", systemContent);

        StringBuilder user = new StringBuilder();
        appendReferenceContext(user, request.referenceContext());
        appendPriorContext(user, request.priorContext());
        appendGuidance(user, request.userGuidance(), "comment on");
        user.append("Chapter: ").append(nullToBlank(request.chapterLabel())).append("\n\n");
        user.append("Chapter text:\n\n").append(nullToBlank(request.chapterText()));

        ArrayNode  contents = root.putArray("contents");
        ObjectNode userTurn = contents.addObject();
        userTurn.put("role", "user");
        userTurn.putArray("parts").addObject().put("text", user.toString());

        root.putObject("generationConfig").put("maxOutputTokens", MAX_TOKENS_EDITORIAL);

        return serialize(root, "editorial");
    }

    /**
     * Codex fill body. Uses {@code responseMimeType:"application/json"} in
     * {@code generationConfig} to enforce a pure-JSON response, matching the
     * review call pattern.
     */
    private String buildCodexFillRequestBody(String model, CodexFillRequest request) {
        ObjectNode root = mapper.createObjectNode();

        root.putObject("systemInstruction")
                .putArray("parts")
                .addObject().put("text", CODEX_FILL_SYSTEM);

        StringBuilder user = new StringBuilder();
        user.append("Entry: ").append(nullToBlank(request.entryTitle())).append("\n");
        user.append("Category: ").append(nullToBlank(request.categoryLabel())).append("\n\n");

        if (request.schemaDescription() != null && !request.schemaDescription().isBlank()) {
            user.append(request.schemaDescription().strip()).append("\n\n");
        }

        String existing = request.existingFields();
        if (existing != null && !existing.isBlank() && !"none".equalsIgnoreCase(existing.strip())) {
            user.append("Author-provided field values (do not contradict):\n")
                .append(existing.strip()).append("\n\n")
                .append("----------------------------------------\n\n");
        }

        if (request.referenceContext() != null && !request.referenceContext().isBlank()) {
            user.append("Other codex entries for reference — established facts to respect:\n\n")
                .append(request.referenceContext().strip()).append("\n\n")
                .append("----------------------------------------\n\n");
        }

        if (request.manuscriptContext() != null && !request.manuscriptContext().isBlank()) {
            user.append("Chapter summaries from the manuscript (use these to fill in the entry):\n\n")
                .append(request.manuscriptContext().strip()).append("\n\n")
                .append("----------------------------------------\n\n");
        }

        appendGuidance(user, request.userGuidance(), "fill in");

        user.append("Now fill in the codex entry for \"").append(nullToBlank(request.entryTitle()))
            .append("\" using the context above.");

        ArrayNode  contents = root.putArray("contents");
        ObjectNode userTurn = contents.addObject();
        userTurn.put("role", "user");
        userTurn.putArray("parts").addObject().put("text", user.toString());

        ObjectNode genConfig = root.putObject("generationConfig");
        genConfig.put("maxOutputTokens", MAX_TOKENS_CODEX_FILL);
        genConfig.put("responseMimeType", "application/json");

        return serialize(root, "codex-fill");
    }

    private CodexFillResult parseCodexFillResult(String content) throws AiProviderException {
        String json = stripCodeFences(content);
        try {
            JsonNode root = mapper.readTree(json);
            Map<String, String> fields = new LinkedHashMap<>();
            JsonNode fieldsNode = root.path("fields");
            if (fieldsNode.isObject()) {
                Set<Entry<String, JsonNode>> set = fieldsNode.properties();
                for (Entry<String, JsonNode> e : set) {
                    if (!e.getValue().isNull() && !e.getValue().isMissingNode()) {
                        fields.put(e.getKey(), e.getValue().asText(""));
                    }
                }
            }
            String body = textOrNull(root, "body");
            return new CodexFillResult(fields, body == null ? "" : body, content, CODEX_FILL_PROMPT_VERSION);
        } catch (Exception e) {
            logger.warn("Failed to parse Gemini codex-fill JSON: {}", e.getMessage());
            throw new AiProviderException("Codex fill response could not be parsed as JSON");
        }
    }

    private String buildWeatherRequestBody(String model, WeatherInterpretationRequest request) {
        ObjectNode root = mapper.createObjectNode();

        root.putObject("systemInstruction")
                .putArray("parts")
                .addObject().put("text", WEATHER_SYSTEM);

        String sceneContext = (request.sceneContext() != null && !request.sceneContext().isBlank())
                ? request.sceneContext()
                : "(none provided)";

        String userContent = """
                Weather facts:
                %s

                Optional scene context:
                %s
                """.formatted(request.weatherFacts(), sceneContext);

        ArrayNode  contents = root.putArray("contents");
        ObjectNode userTurn = contents.addObject();
        userTurn.put("role", "user");
        userTurn.putArray("parts").addObject().put("text", userContent);

        root.putObject("generationConfig").put("maxOutputTokens", MAX_TOKENS_WEATHER);

        return serialize(root, "weather");
    }

    // -------------------------------------------------------------------------
    // Prompt assembly helpers
    // -------------------------------------------------------------------------

    private String reviewSystemPrompt(String unit, List<String> categories, String formInstructions) {
        String form = (formInstructions == null || formInstructions.isBlank())
                ? FORM_FALLBACK
                : formInstructions.strip();
        return form + "\n\n" + functionalBlock(unit, categories);
    }

    private static final String FORM_FALLBACK = "You are an experienced editor reviewing a section of a novel. You do not "
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

    private String reviewUserPrompt(ReviewRequest request) {
        String        unit    = scopeWord(request);
        String        heading = capitalize(unit);
        StringBuilder sb      = new StringBuilder();

        appendReferenceContext(sb, request.referenceContext());
        if (request.priorContext() != null && !request.priorContext().isBlank()) {
            sb.append("Story so far — memory documents of the preceding chapters, for continuity ")
                    .append("context only. Do not review these summaries:\n\n")
                    .append(request.priorContext().strip()).append("\n\n")
                    .append("----------------------------------------\n\n");
            sb.append("Review the following ").append(unit).append(".\n\n");
        }
        appendGuidance(sb, request.userGuidance(), "review");

        sb.append(heading).append(": ").append(nullToBlank(request.unitLabel()));
        if (request.subtitle() != null && !request.subtitle().isBlank()) {
            sb.append(" — ").append(request.subtitle().trim());
        }
        sb.append("\n\n").append(heading).append(" text:\n\n");
        sb.append(nullToBlank(request.text()));
        return sb.toString();
    }

    private static final String MEMORY_WRAPPER = """
            You are summarizing one chapter of a novel to build a running memory \
            document used as continuity context for later editorial review. Read the \
            chapter text and fill in the template below for this chapter. Output only \
            the filled-in template, following its headings and structure exactly. Base \
            every statement strictly on what is present in the chapter text — do not \
            invent, infer beyond the text, or speculate. Be concise and factual. Where \
            the template shows a chapter placeholder, use the chapter's label.""";

    private static final String CHAPTER_SUMMARY_WRAPPER = """
            You are summarizing one chapter of a novel. Write a single, clear, \
            human-readable paragraph that captures what happens in the chapter: the \
            key events, the characters involved, and how the chapter moves the story \
            forward. Write in flowing prose — no headings, no bullet points, no lists, \
            no preamble or labels. Base every statement strictly on the chapter text; \
            do not invent or speculate. Output only the summary paragraph.""";

    private static final String BOOK_SUMMARY_WRAPPER = """
            You are writing a synopsis of an entire novel from the per-chapter \
            summaries provided below, which are given in reading order. Synthesize \
            them into one cohesive, human-readable overview of the whole book — the \
            overall arc, the principal characters, and how the story resolves. Write \
            in flowing prose with no headings, bullet points, or lists, and no \
            preamble or labels. Use no more than %1$d words; do not exceed this \
            limit. Base everything strictly on the supplied chapter summaries; do not \
            invent material not present in them. Output only the synopsis.""";

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

    private static final String CODEX_FILL_SYSTEM = """
            You are an expert creative writing assistant helping an author build a \
            codex (a world-building knowledge base) for their fiction project.

            Your task is to fill in structured fields and write a prose description \
            for a codex entry based on the manuscript content provided.

            Rules:
            - Base every answer strictly on the manuscript and existing codex entries provided.
            - Do not invent details not present in the text.
            - Respect existing field values — if the author has already filled in a field, \
              do not contradict it; you may add to it or leave it, but never contradict.
            - For SELECT fields, the value MUST be one of the listed valid options, or an \
              empty string if you cannot determine the right option.
            - If you cannot determine a value from the context, leave that field as an \
              empty string — never guess or fabricate.
            - The body field is a single flowing prose paragraph describing the entry overall \
              (plain text, no HTML, no Markdown). Leave it as an empty string if there is \
              insufficient information.

            Return ONLY a JSON object — no prose, no Markdown, no code fences — in exactly \
            this shape: {"fields":{"field_key":"value"},"body":"prose paragraph"}""";

    private static final String WEATHER_SYSTEM = """
            You are a creative writing assistant. Given raw weather facts and optional \
            scene context, write a vivid, atmospheric paragraph (2-4 sentences) describing \
            the weather as a novelist would experience it in that scene. Be sensory and \
            evocative. Output only the paragraph — no preamble, no labels.""";

    // -------------------------------------------------------------------------
    // Shared prompt-fragment helpers
    // -------------------------------------------------------------------------

    private static void appendReferenceContext(StringBuilder sb, String referenceContext) {
        if (referenceContext != null && !referenceContext.isBlank()) {
            sb.append("Reference material — established canon and voice the manuscript must respect. ")
                    .append("Entries may list structured canonical fields (labeled) and a description. ")
                    .append("Use it to judge the chapter, but do not comment on it directly:\n\n")
                    .append(referenceContext.strip()).append("\n\n")
                    .append("----------------------------------------\n\n");
        }
    }

    private static void appendPriorContext(StringBuilder sb, String priorContext) {
        if (priorContext != null && !priorContext.isBlank()) {
            sb.append("Story so far — memory documents of the preceding chapters, for continuity ")
                    .append("context only. Do not comment on these summaries:\n\n")
                    .append(priorContext.strip()).append("\n\n")
                    .append("----------------------------------------\n\n");
        }
    }

    private static void appendGuidance(StringBuilder sb, String guidance, String verb) {
        if (guidance != null && !guidance.isBlank()) {
            sb.append("Additional guidance from the author for this ").append(verb)
                    .append(" only — follow it, but it is not material to ").append(verb).append(":\n\n")
                    .append(guidance.strip()).append("\n\n")
                    .append("----------------------------------------\n\n");
        }
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    /**
     * Extracts text from the Gemini generateContent response.
     * The response shape is
     * {@code {"candidates":[{"content":{"parts":[{"text":"..."}]}}]}}.
     *
     * <p>
     * Gemini 2.5 and later hybrid-reasoning models include internal thinking
     * tokens as parts with {@code "thought": true} before the actual output
     * part. These parts must be skipped; returning them would produce a
     * mid-sentence fragment of the model's internal reasoning instead of the
     * real response. This manifests on free-text calls (editorial, memory,
     * summary) but not on JSON-mode review calls, where {@code responseMimeType}
     * suppresses exposed thought parts.
     */
    private String extractContent(String responseBody) throws AiProviderException {
        try {
            JsonNode root       = mapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new AiProviderException("Gemini returned no candidates");
            }
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new AiProviderException("Gemini returned no content parts");
            }
            for (JsonNode part : parts) {
                if (part.path("thought").asBoolean(false)) {
                    continue;
                }
                String text = part.path("text").asText("");
                if (!text.isBlank()) {
                    return text;
                }
            }
            throw new AiProviderException("Gemini returned no text content");
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Could not read Gemini response: " + e.getMessage(), e);
        }
    }

    private List<ReviewResult.Recommendation> parseRecommendations(String content) throws AiProviderException {
        String json = stripCodeFences(content);
        try {
            logger.debug("Parsing Gemini review JSON: contentChars={}, jsonChars={}", lengthOf(content), lengthOf(json));
            JsonNode root  = mapper.readTree(json);
            JsonNode array = root.path("recommendations");
            if (!array.isArray()) {
                throw new AiProviderException("Gemini response did not contain a recommendations array");
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
                if (recommendation == null || recommendation.isBlank())
                    continue;
                result.add(new ReviewResult.Recommendation(
                        category, severity, location, recommendation,
                        codexCategory, codexTitle, anchorText));
            }
            logger.debug("Parsed Gemini review JSON: recommendations={}", result.size());
            return result;
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Failed to parse Gemini recommendations JSON", e);
            throw new AiProviderException("Gemini returned a response that could not be parsed as review JSON");
        }
    }

    private String extractErrorMessage(int status, String body) {
        try {
            JsonNode error   = mapper.readTree(body).path("error");
            String   message = error.path("message").asText("");
            if (!message.isBlank()) {
                return "Gemini error (HTTP " + status + "): " + message;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "Gemini request failed (HTTP " + status + ")";
    }

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    private String serialize(ObjectNode root, String callType) {
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Gemini " + callType + " request body", e);
        }
    }

    private static String resolveModel(String requested, String fallback) {
        return (requested == null || requested.isBlank()) ? fallback : requested.trim();
    }

    private static String scopeWord(ReviewRequest request) {
        String word = request.scopeWord();
        return (word == null || word.isBlank()) ? "chapter" : word.trim().toLowerCase();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty())
            return "";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String stripCodeFences(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0)
                trimmed = trimmed.substring(firstNewline + 1);
            if (trimmed.endsWith("```"))
                trimmed = trimmed.substring(0, trimmed.length() - 3);
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
        if (value == null || value.isBlank())
            return "";
        String stripped = value.strip().replaceAll("\\s+", " ");
        return stripped.length() <= 120 ? stripped : stripped.substring(0, 120) + "...";
    }
}
