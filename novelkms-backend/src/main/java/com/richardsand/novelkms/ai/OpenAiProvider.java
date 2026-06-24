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
 *       contract is identical across scopes; {@code chapter-review-v3} marks the
 *       move to a scope-general prompt.</li>
 * </ul>
 */
public class OpenAiProvider implements AiProvider {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiProvider.class);

    public static final  String PROVIDER_KEY   = "OPENAI";
    public static final  String DEFAULT_MODEL  = "gpt-5.4";
    public static final  String PROMPT_VERSION = "chapter-review-v3";

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

        String body = buildRequestBody(model, request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + request.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new AiProviderException("Could not reach OpenAI: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("OpenAI request was interrupted", e);
        }

        if (response.statusCode() != 200) {
            throw new AiProviderException(extractErrorMessage(response.statusCode(), response.body()));
        }

        String content = extractContent(response.body());
        List<ReviewResult.Recommendation> recs = parseRecommendations(content);
        return new ReviewResult(recs, content, PROMPT_VERSION);
    }

    private String buildRequestBody(String model, ReviewRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);

        ArrayNode messages = root.putArray("messages");

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", systemPrompt(scopeWord(request), request.categories()));

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

    private String systemPrompt(String unit, List<String> categories) {
        String categoryList = (categories == null || categories.isEmpty())
                ? "Continuity, Characterization, Pacing, Dialogue, Clarity, Grammar, General Notes"
                : String.join(", ", categories);
        // %1$s = the scope unit word ("chapter"/"scene"); %2$s = the category list.
        return """
                You are an experienced developmental and line editor reviewing a single %1$s \
                of a novel. You do not rewrite the manuscript. You produce specific, atomic, \
                independently actionable editorial recommendations.

                Each recommendation must:
                - address exactly one issue;
                - be concrete (cite the specific moment, line, or transition it refers to) \
                  rather than vague ("this %1$s needs work" is not acceptable);
                - fall under one of these categories: %2$s;
                - carry a severity of LOW, MEDIUM, or HIGH.

                For each recommendation also suggest how it could be filed in the project's \
                knowledge base (the "codex"), so the author can save it in one click:
                - codexCategory: exactly one of CHARACTER, VOICE, PLOT, WORLD, TIMELINE, CANON, NOTES. \
                  Use CANON for established facts, rules, or continuity points the author should lock in \
                  (e.g. a detail that must stay consistent later); CHARACTER for character facts/arcs; \
                  VOICE for how a character speaks; WORLD for setting/institutions/objects; \
                  TIMELINE for dates and ordering; PLOT for plot threads; NOTES for anything else.
                - codexTitle: a short (3-8 word) title for that codex entry.

                For each recommendation, include an anchorText field: a short verbatim quote \
                (5-30 words) copied exactly from the %1$s text that identifies the passage \
                your recommendation refers to. This quote will be used to scroll the author's \
                editor to the relevant passage, so it must appear word-for-word in the %1$s.

                Respond with a single JSON object and nothing else, in exactly this shape:
                {"recommendations":[{"category":"...","severity":"LOW|MEDIUM|HIGH",\
                "location":"where in the %1$s this applies","recommendation":"the note",\
                "codexCategory":"CANON","codexTitle":"short entry title",\
                "anchorText":"verbatim quote from the %1$s"}]}

                If the %1$s is strong and you have no substantive notes, return \
                {"recommendations":[]}.""".formatted(unit, categoryList);
    }

    private String userPrompt(ReviewRequest request) {
        String unit = scopeWord(request);
        String heading = capitalize(unit);
        StringBuilder sb = new StringBuilder();
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
}
