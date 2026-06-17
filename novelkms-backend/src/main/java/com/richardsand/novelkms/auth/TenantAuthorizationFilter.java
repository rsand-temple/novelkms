package com.richardsand.novelkms.auth;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.richardsand.novelkms.dao.TenantAccessDao;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.AUTHORIZATION)
public class TenantAuthorizationFilter implements ContainerRequestFilter {
    private static final Set<String> PUBLIC_PREFIXES = Set.of("auth/", "healthcheck");

    private final TenantAccessDao access;
    private final ObjectMapper mapper;

    @Inject
    public TenantAuthorizationFilter(TenantAccessDao access, ObjectMapper mapper) {
        this.access = access;
        this.mapper = mapper;
    }

    @Override
    public void filter(ContainerRequestContext request) throws IOException {
        String path = trim(request.getUriInfo().getPath());
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) return;

        UUID userId = CurrentUser.id(request);
        try {
            // Admin operations are application-wide and are not user endpoints.
            if (path.startsWith("admin/")) {
                deny(request);
                return;
            }

            authorizePathIds(request, path, userId);
            authorizeSensitiveJsonBody(request, path, userId);
        } catch (Exception e) {
            if (e instanceof IOException io) throw io;
            throw new IOException("Tenant authorization failed", e);
        }
    }

    private void authorizePathIds(ContainerRequestContext request, String path, UUID userId) throws Exception {
        String[] s = path.split("/");
        for (int i = 0; i + 1 < s.length; i++) {
            UUID id = parseUuid(s[i + 1]);
            if (id == null) continue;

            boolean owned = switch (s[i].toLowerCase(Locale.ROOT)) {
                case "projects" -> access.ownsProject(userId, id);
                case "books" -> access.ownsBook(userId, id);
                case "parts" -> access.ownsPart(userId, id);
                case "chapters" -> access.ownsChapter(userId, id);
                case "scenes" -> access.ownsScene(userId, id);
                default -> true;
            };
            if (!owned) {
                notFound(request);
                return;
            }
        }

        // Export routes put the entity type one segment before the UUID.
        if (s.length >= 3 && "export".equals(s[0])) {
            UUID id = parseUuid(s[2]);
            if (id != null) {
                boolean owned = switch (s[1]) {
                    case "books" -> access.ownsBook(userId, id);
                    case "parts" -> access.ownsPart(userId, id);
                    case "chapters" -> access.ownsChapter(userId, id);
                    case "scenes" -> access.ownsScene(userId, id);
                    default -> false;
                };
                if (!owned) notFound(request);
            }
        }
    }

    private void authorizeSensitiveJsonBody(ContainerRequestContext request, String path, UUID userId) throws Exception {
        MediaType type = request.getMediaType();
        if (type == null || !type.isCompatible(MediaType.APPLICATION_JSON_TYPE) || !request.hasEntity()) return;

        boolean inspect = path.endsWith("/move")
                || path.endsWith("/reorder")
                || ("POST".equalsIgnoreCase(request.getMethod()) && path.matches("books/[0-9a-fA-F-]+/chapters"));
        if (!inspect) return;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        request.getEntityStream().transferTo(out);
        byte[] bytes = out.toByteArray();
        request.setEntityStream(new ByteArrayInputStream(bytes));
        if (bytes.length == 0) return;

        JsonNode root = mapper.readTree(bytes);
        if (!allUuidFieldsOwned(root, null, userId)) deny(request);
    }

    private boolean allUuidFieldsOwned(JsonNode node, String fieldName, UUID userId) throws Exception {
        if (node == null || node.isNull()) return true;
        if (node.isObject()) {
            for (Entry<String, JsonNode> prop : node.properties()) {
                if (!allUuidFieldsOwned(prop.getValue(), prop.getKey(), userId)) return false;
            }
            return true;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (!allUuidFieldsOwned(child, fieldName, userId)) return false;
            }
            return true;
        }
        if (!node.isTextual() || fieldName == null) return true;

        String lower = fieldName.toLowerCase(Locale.ROOT);
        if (!(lower.equals("id") || lower.endsWith("id") || lower.endsWith("ids"))) return true;
        UUID id = parseUuid(node.asText());
        return id == null || access.ownsAnyEntity(userId, id);
    }

    private static UUID parseUuid(String value) {
        try { return UUID.fromString(value); }
        catch (Exception ignored) { return null; }
    }

    private static String trim(String path) {
        if (path == null) return "";
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static void deny(ContainerRequestContext request) {
        request.abortWith(Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "forbidden"))
                .build());
    }

    private static void notFound(ContainerRequestContext request) {
        request.abortWith(Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", "not_found"))
                .build());
    }
}
