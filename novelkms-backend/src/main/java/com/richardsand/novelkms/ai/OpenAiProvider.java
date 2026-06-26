package com.richardsand.novelkms.ai;

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

/**
 * OpenAI implementation of {@link AiProvider}, calling the Chat Completions API
 * directly over {@link java.net.http.HttpClient} (no SDK dependency, consistent
 * with NovelKMS's hand-rolled-integration ethos).
 *
 * <p>Design choices:
 * <ul>
 *   <li>Uses {@code response_format: {"type":"json_object"}} rather than a strict
 *       {@code json_schema}. JSON-object mode is supported across a broad range
 *       of models, which matters for a BYOK tool where the user picks the model.
 *       The required output shape is described in the system prompt instead.</li>
 *   <li>Sends only {@code model}, {@code messages}, and {@code response_format} —
 *       no {@code temperature} or token caps — so requests don't 400 on reasoning
 *       models that reject those parameters.</li>
 *   <li>The prompt is scope-aware: the same template reviews a "chapter" or a
 *       "scene" depending on {@link ReviewRequest#scopeWord()}. The output
 *       contract is identical across scopes; {@code chapter-review-v3} marked the
 *       move to a scope-general prompt.</li>
 *   <li>The system prompt is assembled from two parts: a <b>form</b> block —
 *       the editorial persona/constraints, author-editable and supplied via
 *       {@link ReviewRequest#formInstructions()} — followed by a constant
 *       <b>functional</b> block here that defines the JSON output contract
 *       NovelKMS consumes. The form block never breaks parsing because the
 *       functional block fully specifies the required shape. {@code chapter-review-v4}
 *       marks externalizing the form block.</li>
 *   <li>Every generation call (review, memory, chapter summary, book summary)
 *       accepts an optional one-time {@code userGuidance} string — a free-text
 *       author note for that single call only, unrelated to the persistent
 *       form/template override cascades. When present it is appended to the
 *       <em>user</em> message as a clearly-fenced addendum, closest to the
 *       material it concerns, so the model treats it as an instruction rather
 *       than as content to summarize or review.</li>
 * </ul>
 */
public class OpenAiProvider implements AiProvider {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiProvider.class);

    public static final  String PROVIDER_KEY   = "OPENAI";
    public static final  String DEFAULT_MODEL  = "gpt-5.4";
    // chapter-review-v6: generation calls can now carry an optional one-time
    // userGuidance addendum from the author, alongside the existing "story so
    // far" and reference blocks. The JSON output contract is unchanged from v4.
    public static final  String PROMPT_VERSION = "chapter-review-v6";
    /** Memory-document generation prompt version (free-text output; no JSON contract). */
    public static final  String MEMORY_PROMPT_VERSION = "memory-v2";
    /** Chapter-summary generation prompt version (free-text paragraph; no JSON contract). */
    public static final  String CHAPTER_SUMMARY_PROMPT_VERSION = "chapter-summary-v2";
    /** Book-summary generation prompt version (free-text synopsis built from chapter summaries). */
    public static final  String BOOK_SUMMARY_PROMPT_VERSION = "book-summary-v2";

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
        logger.debug("OpenAI chapter-summary request context: chapterTextChars={}, userGuidanceChars={}",
                lengthOf(request.chapterText()), lengthOf(request.userGuidance()));

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
        logger.debug("OpenAI book-summary request context: chapterSummariesChars={}, userGuidanceChars={}",
                lengthOf(request.chapterSummaries()), lengthOf(request.userGuidance()));

        String body = buildBookSummaryRequestBody(model, request);
        String content = postForContent(request.apiKey(), body);
        logger.info("OpenAI book-summary request completed: model={}, outputChars={}",
                model, lengthOf(content));
        return new SummaryResult(content.strip(), BOOK_SUMMARY_PROMPT_VERSION);
    }

    /**
     * Sends a Chat Completions request and returns the assistant message content,
     * translating transport and non-200 responses into {@link AiProviderException}.
     * Shared by {@link #review} and {@link #generateMemory}.
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
            // ObjectNode serialization does not realistically fail.
            throw new IllegalStateException("Failed to build OpenAI request body", e);
        }
    }

    /**
     * Assembles the system prompt as {@code form + "\n\n" + functional}.
     *
     * <p>{@code form} is the author-supplied editorial persona/constraints (or
     * the system default, resolved upstream). {@code functional} is the constant
     * JSON output contract NovelKMS consumes — it owns all scope-awareness
     * ({@code %1$s} = unit word) and the category roster ({@code %2$s}), so the
     * form block can be edited freely without ever breaking parsing.
     */
    private String systemPrompt(String unit, List<String> categories, String formInstructions) {
        String form = (formInstructions == null || formInstructions.isBlank())
                ? FORM_FALLBACK
                : formInstructions.strip();
        return form + "\n\n" + functionalBlock(unit, categories);
    }

    /**
     * Minimal safety net only. The real default ("form") text lives in
     * {@code AiFormInstructionsDefaults} and is always resolved upstream by
     * {@code AiReviewService}; this guards against a future caller that forgets
     * to set it, keeping the {@code ai} package free of a {@code model} import.
     */
    private static final String FORM_FALLBACK =
            "You are an experienced editor reviewing a section of a novel. You do not "
            + "rewrite the manuscript; you produce specific, actionable editorial notes.";

    private String functionalBlock(String unit, List<String> categories) {
        String categoryList = (categories == null || categories.isEmpty())
                ? "Continuity, Characterization, Pacing, Dialogue, Clarity, Grammar, General Notes"
                : String.join(", ", categories);
        // %1$s = the scope unit word ("chapter"/"scene"); %2$s = the category list.
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

                If you have no substantive notes, return {"recommendations":[]}.""".formatted(unit, categoryList);
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

        // Free-text output: no response_format and no token caps (reasoning-model safe).
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            // ObjectNode serialization does not realistically fail.
            throw new IllegalStateException("Failed to build OpenAI memory request body", e);
        }
    }

    /** System instruction for chapter-summary generation (one readable paragraph). */
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

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", CHAPTER_SUMMARY_WRAPPER);

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

        // Free-text output: no response_format and no token caps (reasoning-model safe).
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build OpenAI chapter-summary request body", e);
        }
    }

    /**
     * System instruction for book-summary generation. The model sees only the
     * chapter summaries (assembled in book order), never the manuscript, and is
     * held to a hard word ceiling; {@code %1$d} is that ceiling.
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

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", BOOK_SUMMARY_WRAPPER.formatted(maxWords));

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

        // Free-text output: no response_format and no token caps (reasoning-model safe).
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build OpenAI book-summary request body", e);
        }
    }

    private String userPrompt(ReviewRequest request) {
        String unit = scopeWord(request);
        String heading = capitalize(unit);
        StringBuilder sb = new StringBuilder();

        // Optional context blocks, clearly fenced off as background — not material
        // to be reviewed. Reference material first, then the running "story so far".
        if (request.referenceContext() != null && !request.referenceContext().isBlank()) {
            sb.append("Reference material — established canon and voice the manuscript must respect. ")
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

    private String extractContent(String responseBody) throws AiProviderException {
        try {
            JsonNode root = mapper.readTree(responseBody);
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
            JsonNode root = mapper.readTree(json);
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
            JsonNode error = mapper.readTree(body).path("error");
            String message = error.path("message").asText("");
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
