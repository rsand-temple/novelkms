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
 * Anthropic implementation of {@link AiProvider}, calling the Messages API
 * directly over {@link java.net.http.HttpClient} (no SDK dependency, consistent
 * with NovelKMS's hand-rolled-integration ethos).
 *
 * <p>
 * Design notes vs. {@link OpenAiProvider}:
 * <ul>
 * <li>Auth uses {@code x-api-key} and {@code anthropic-version} headers rather
 * than {@code Authorization: Bearer}.</li>
 * <li>The Anthropic Messages API takes the system prompt as a top-level
 * {@code "system"} string rather than a {@code {"role":"system"}} message,
 * so the {@code messages} array contains only the user turn.</li>
 * <li>{@code max_tokens} is required by the Anthropic API. Reasonable ceilings
 * are set per call type; they are not author-configurable because Anthropic
 * models reliably stop at natural end-of-output before the ceiling.</li>
 * <li>Anthropic has no {@code response_format: json_object} equivalent. The
 * functional block already demands pure JSON with no prose or fences, and
 * the existing {@link #stripCodeFences} helper handles any edge cases. The
 * same {@code parseRecommendations} logic is used as in OpenAiProvider.</li>
 * <li>Prompt version constants are identical to those in OpenAiProvider because
 * the prompt content is the same — only the transport format differs.</li>
 * </ul>
 */
public class AnthropicProvider implements AiProvider {
    private static final Logger logger = LoggerFactory.getLogger(AnthropicProvider.class);

    public static final String PROVIDER_KEY  = "ANTHROPIC";
    public static final String DEFAULT_MODEL = "claude-sonnet-4-6";

    /** Reuse the same prompt-version labels; the prompt content is provider-agnostic. */
    public static final String PROMPT_VERSION                 = OpenAiProvider.PROMPT_VERSION;
    public static final String MEMORY_PROMPT_VERSION          = OpenAiProvider.MEMORY_PROMPT_VERSION;
    public static final String CHAPTER_SUMMARY_PROMPT_VERSION = OpenAiProvider.CHAPTER_SUMMARY_PROMPT_VERSION;
    public static final String BOOK_SUMMARY_PROMPT_VERSION    = OpenAiProvider.BOOK_SUMMARY_PROMPT_VERSION;
    public static final String EDITORIAL_PROMPT_VERSION       = OpenAiProvider.EDITORIAL_PROMPT_VERSION;
    public static final String WEATHER_INTERPRETATION_VERSION = OpenAiProvider.WEATHER_INTERPRETATION_PROMPT_VERSION;

    private static final String ENDPOINT          = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /**
     * max_tokens ceilings per call type. Anthropic requires this field;
     * models stop naturally well before these limits in practice.
     */
    private static final int MAX_TOKENS_REVIEW    = 4096;
    private static final int MAX_TOKENS_MEMORY    = 2048;
    private static final int MAX_TOKENS_SUMMARY   = 2048;
    private static final int MAX_TOKENS_EDITORIAL = 1024;
    private static final int MAX_TOKENS_WEATHER   = 1024;

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
    // AiProvider interface — all six methods
    // -------------------------------------------------------------------------

    @Override
    public ReviewResult review(ReviewRequest request) throws AiProviderException {
        String model = resolveModel(request.model(), DEFAULT_MODEL);

        logger.info("Anthropic review request started: model={}, scope={}, unitLabel={}, promptVersion={}",
                model, scopeWord(request), safeLabel(request.unitLabel()), PROMPT_VERSION);
        logger.debug("Anthropic review request context: textChars={}, priorContextChars={}, referenceContextChars={}, userGuidanceChars={}, categoryCount={}",
                lengthOf(request.text()), lengthOf(request.priorContext()), lengthOf(request.referenceContext()),
                lengthOf(request.userGuidance()),
                request.categories() == null ? 0 : request.categories().size());

        String                            body    = buildReviewRequestBody(model, request);
        String                            content = postForContent(request.apiKey(), body);
        List<ReviewResult.Recommendation> recs    = parseRecommendations(content);
        logger.info("Anthropic review request completed: model={}, recommendations={}", model, recs.size());
        return new ReviewResult(recs, content, PROMPT_VERSION);
    }

    @Override
    public MemoryResult generateMemory(MemoryRequest request) throws AiProviderException {
        String model = resolveModel(request.model(), DEFAULT_MODEL);

        logger.info("Anthropic memory-generation request started: model={}, chapterLabel={}, promptVersion={}",
                model, safeLabel(request.chapterLabel()), MEMORY_PROMPT_VERSION);
        logger.debug("Anthropic memory-generation request context: chapterTextChars={}, templateChars={}, userGuidanceChars={}",
                lengthOf(request.chapterText()), lengthOf(request.template()), lengthOf(request.userGuidance()));

        String body    = buildMemoryRequestBody(model, request);
        String content = postForContent(request.apiKey(), body);
        logger.info("Anthropic memory-generation request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new MemoryResult(content.strip(), MEMORY_PROMPT_VERSION);
    }

    @Override
    public SummaryResult generateChapterSummary(SummaryRequest request) throws AiProviderException {
        String model = resolveModel(request.model(), DEFAULT_MODEL);

        logger.info("Anthropic chapter-summary request started: model={}, chapterLabel={}, promptVersion={}",
                model, safeLabel(request.chapterLabel()), CHAPTER_SUMMARY_PROMPT_VERSION);
        logger.debug("Anthropic chapter-summary request context: chapterTextChars={}, userGuidanceChars={}, hasCustomPrompt={}",
                lengthOf(request.chapterText()), lengthOf(request.userGuidance()),
                request.systemPrompt() != null && !request.systemPrompt().isBlank());

        String body    = buildChapterSummaryRequestBody(model, request);
        String content = postForContent(request.apiKey(), body);
        logger.info("Anthropic chapter-summary request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new SummaryResult(content.strip(), CHAPTER_SUMMARY_PROMPT_VERSION);
    }

    @Override
    public SummaryResult generateBookSummary(BookSummaryRequest request) throws AiProviderException {
        String model = resolveModel(request.model(), DEFAULT_MODEL);

        logger.info("Anthropic book-summary request started: model={}, bookTitle={}, maxWords={}, promptVersion={}",
                model, safeLabel(request.bookTitle()), request.maxWords(), BOOK_SUMMARY_PROMPT_VERSION);
        logger.debug("Anthropic book-summary request context: chapterSummariesChars={}, userGuidanceChars={}, hasCustomPrompt={}",
                lengthOf(request.chapterSummaries()), lengthOf(request.userGuidance()),
                request.systemPrompt() != null && !request.systemPrompt().isBlank());

        String body    = buildBookSummaryRequestBody(model, request);
        String content = postForContent(request.apiKey(), body);
        logger.info("Anthropic book-summary request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new SummaryResult(content.strip(), BOOK_SUMMARY_PROMPT_VERSION);
    }

    @Override
    public EditorialResult generateEditorial(EditorialRequest request) throws AiProviderException {
        String model = resolveModel(request.model(), DEFAULT_MODEL);

        logger.info("Anthropic editorial request started: model={}, chapterLabel={}, promptVersion={}",
                model, safeLabel(request.chapterLabel()), EDITORIAL_PROMPT_VERSION);
        logger.debug("Anthropic editorial request context: chapterTextChars={}, priorContextChars={}, referenceContextChars={}, userGuidanceChars={}, hasCustomPrompt={}",
                lengthOf(request.chapterText()), lengthOf(request.priorContext()),
                lengthOf(request.referenceContext()), lengthOf(request.userGuidance()),
                request.systemPrompt() != null && !request.systemPrompt().isBlank());

        String body    = buildEditorialRequestBody(model, request);
        String content = postForContent(request.apiKey(), body);
        logger.info("Anthropic editorial request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new EditorialResult(content.strip(), EDITORIAL_PROMPT_VERSION);
    }

    @Override
    public WeatherInterpretationResult interpretWeather(WeatherInterpretationRequest request) throws AiProviderException {
        String model = resolveModel(request.model(), DEFAULT_MODEL);

        logger.info("Anthropic weather-interpretation request started: model={}, promptVersion={}",
                model, WEATHER_INTERPRETATION_VERSION);
        logger.debug("Anthropic weather-interpretation context: weatherFactsChars={}, sceneContextChars={}",
                lengthOf(request.weatherFacts()), lengthOf(request.sceneContext()));

        String body    = buildWeatherRequestBody(model, request);
        String content = postForContent(request.apiKey(), body);
        logger.info("Anthropic weather-interpretation request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new WeatherInterpretationResult(content.strip(), WEATHER_INTERPRETATION_VERSION);
    }

    // -------------------------------------------------------------------------
    // HTTP transport
    // -------------------------------------------------------------------------

    /**
     * Posts to the Anthropic Messages API and returns the first text-block
     * content string, translating transport and non-200 responses into
     * {@link AiProviderException}.
     */
    private String postForContent(String apiKey, String body) throws AiProviderException {
        logger.debug("Anthropic HTTP request prepared: endpoint={}, requestBytes={}",
                ENDPOINT, body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            logger.warn("Anthropic HTTP request failed before response: {}", e.getMessage());
            throw new AiProviderException("Could not reach Anthropic: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Anthropic HTTP request interrupted");
            throw new AiProviderException("Anthropic request was interrupted", e);
        }

        if (response.statusCode() != 200) {
            logger.warn("Anthropic HTTP request returned non-success status: status={}, responseChars={}",
                    response.statusCode(), lengthOf(response.body()));
            throw new AiProviderException(extractErrorMessage(response.statusCode(), response.body()));
        }

        logger.debug("Anthropic HTTP request succeeded: status={}, responseChars={}",
                response.statusCode(), lengthOf(response.body()));
        return extractContent(response.body());
    }

    // -------------------------------------------------------------------------
    // Request body builders
    // -------------------------------------------------------------------------

    private String buildReviewRequestBody(String model, ReviewRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", MAX_TOKENS_REVIEW);

        // Anthropic: system is a top-level string, not a message.
        String systemContent = reviewSystemPrompt(
                scopeWord(request), request.categories(), request.formInstructions());
        root.put("system", systemContent);

        ArrayNode  messages = root.putArray("messages");
        ObjectNode user     = messages.addObject();
        user.put("role", "user");
        user.put("content", reviewUserPrompt(request));

        return serialize(root, "review");
    }

    private String buildMemoryRequestBody(String model, MemoryRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", MAX_TOKENS_MEMORY);
        root.put("system", MEMORY_WRAPPER + "\n\nTemplate:\n\n" + nullToBlank(request.template()));

        StringBuilder user = new StringBuilder();
        user.append("Chapter: ").append(nullToBlank(request.chapterLabel())).append("\n\n");
        appendGuidance(user, request.userGuidance(), "summarize");
        user.append("Chapter text:\n\n").append(nullToBlank(request.chapterText()));

        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "user").put("content", user.toString());

        return serialize(root, "memory");
    }

    private String buildChapterSummaryRequestBody(String model, SummaryRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", MAX_TOKENS_SUMMARY);

        String systemContent = (request.systemPrompt() != null && !request.systemPrompt().isBlank())
                ? request.systemPrompt()
                : CHAPTER_SUMMARY_WRAPPER;
        root.put("system", systemContent);

        StringBuilder user = new StringBuilder();
        user.append("Chapter: ").append(nullToBlank(request.chapterLabel())).append("\n\n");
        appendGuidance(user, request.userGuidance(), "summarize");
        user.append("Chapter text:\n\n").append(nullToBlank(request.chapterText()));

        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "user").put("content", user.toString());

        return serialize(root, "chapter-summary");
    }

    private String buildBookSummaryRequestBody(String model, BookSummaryRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", MAX_TOKENS_SUMMARY);

        int    maxWords      = request.maxWords() > 0 ? request.maxWords() : 1000;
        String systemContent = (request.systemPrompt() != null && !request.systemPrompt().isBlank())
                ? request.systemPrompt()
                : BOOK_SUMMARY_WRAPPER.formatted(maxWords);
        root.put("system", systemContent);

        StringBuilder user = new StringBuilder();
        if (request.bookTitle() != null && !request.bookTitle().isBlank()) {
            user.append("Book: ").append(request.bookTitle().trim()).append("\n\n");
        }
        appendGuidance(user, request.userGuidance(), "summarize");
        user.append("Chapter summaries (in reading order):\n\n")
                .append(nullToBlank(request.chapterSummaries()));

        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "user").put("content", user.toString());

        return serialize(root, "book-summary");
    }

    private String buildEditorialRequestBody(String model, EditorialRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", MAX_TOKENS_EDITORIAL);

        String systemContent = (request.systemPrompt() != null && !request.systemPrompt().isBlank())
                ? request.systemPrompt()
                : EDITORIAL_WRAPPER;
        root.put("system", systemContent);

        StringBuilder user = new StringBuilder();
        appendReferenceContext(user, request.referenceContext());
        appendPriorContext(user, request.priorContext());
        appendGuidance(user, request.userGuidance(), "comment on");

        user.append("Chapter: ").append(nullToBlank(request.chapterLabel()));
        if (request.subtitle() != null && !request.subtitle().isBlank()) {
            user.append(" — ").append(request.subtitle().trim());
        }
        user.append("\n\nChapter text:\n\n").append(nullToBlank(request.chapterText()));

        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "user").put("content", user.toString());

        return serialize(root, "editorial");
    }

    private String buildWeatherRequestBody(String model, WeatherInterpretationRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", MAX_TOKENS_WEATHER);
        root.put("system", """
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

        String sceneContext = request.sceneContext() == null || request.sceneContext().isBlank()
                ? "No scene context supplied."
                : request.sceneContext().strip();

        String userContent = """
                Weather data:
                %s

                Optional scene context:
                %s
                """.formatted(request.weatherFacts(), sceneContext);

        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "user").put("content", userContent);

        return serialize(root, "weather");
    }

    // -------------------------------------------------------------------------
    // Prompt assembly helpers
    // -------------------------------------------------------------------------

    /**
     * Identical split to OpenAiProvider: {@code form + "\n\n" + functional}.
     * The functional block asks for pure JSON with no prose or fences; Claude
     * follows this reliably without needing {@code response_format}.
     */
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

    /**
     * Appends the one-time author guidance fence to the user message.
     *
     * @param verb the task word for the disclaimer, e.g. "review", "summarize", "comment on"
     */
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
     * Extracts text from the Anthropic Messages API response.
     * The response shape is {@code {"content":[{"type":"text","text":"..."},...]}}.
     */
    private String extractContent(String responseBody) throws AiProviderException {
        try {
            JsonNode root    = mapper.readTree(responseBody);
            JsonNode content = root.path("content");
            if (!content.isArray() || content.isEmpty()) {
                throw new AiProviderException("Anthropic returned no content blocks");
            }
            // Walk blocks and return the first text block.
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText(""))) {
                    String text = block.path("text").asText("");
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }
            throw new AiProviderException("Anthropic returned no text content");
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Could not read Anthropic response: " + e.getMessage(), e);
        }
    }

    private List<ReviewResult.Recommendation> parseRecommendations(String content) throws AiProviderException {
        String json = stripCodeFences(content);
        try {
            logger.debug("Parsing Anthropic review JSON: contentChars={}, jsonChars={}", lengthOf(content), lengthOf(json));
            JsonNode root  = mapper.readTree(json);
            JsonNode array = root.path("recommendations");
            if (!array.isArray()) {
                throw new AiProviderException("Anthropic response did not contain a recommendations array");
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
            logger.debug("Parsed Anthropic review JSON: recommendations={}", result.size());
            return result;
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Failed to parse Anthropic recommendations JSON", e);
            throw new AiProviderException("Anthropic returned a response that could not be parsed as review JSON");
        }
    }

    private String extractErrorMessage(int status, String body) {
        try {
            // Anthropic error shape: {"type":"error","error":{"type":"...","message":"..."}}
            JsonNode error   = mapper.readTree(body).path("error");
            String   message = error.path("message").asText("");
            if (!message.isBlank()) {
                return "Anthropic error (HTTP " + status + "): " + message;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "Anthropic request failed (HTTP " + status + ")";
    }

    // -------------------------------------------------------------------------
    // Utility helpers (mirrors OpenAiProvider)
    // -------------------------------------------------------------------------

    private String serialize(ObjectNode root, String callType) {
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Anthropic " + callType + " request body", e);
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
