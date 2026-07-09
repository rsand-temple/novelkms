package com.richardsand.novelkms.dropwizard.web;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Supports HTML5 history-mode client-side routing (React Router's
 * {@code BrowserRouter}).
 *
 * <p>
 * The {@code AssetsBundle} that serves the built frontend only knows how
 * to resolve files that actually exist under the bundled {@code /webapp}
 * classpath directory (plus the configured index file for the literal
 * {@code "/"} path). It has no concept of client-side routes such as
 * {@code /register} or {@code /login} — those paths exist only in the React
 * Router route table, not as files on disk, so the asset servlet returns 404
 * for them.
 *
 * <p>
 * This filter runs ahead of the asset servlet on every initial
 * (non-forwarded) request. Any GET request that is not an API call (the
 * JAX-RS root path is {@code /api/*}) and whose final path segment has no
 * file extension is treated as a client-side route and forwarded to
 * {@code /index.html} so the SPA can resolve it. Real static assets (e.g.
 * {@code /assets/main-abc123.js}, {@code /favicon.ico}) always have a file
 * extension on their last segment and pass through untouched.
 *
 * <p>
 * The filter is mapped only to the {@code REQUEST} dispatcher type, so
 * the internal forward to {@code /index.html} is not re-evaluated by this
 * filter — it goes straight to the asset servlet, which serves the file
 * normally.
 */
public class SpaFallbackFilter implements Filter {

    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            ".js",
            ".css",
            ".map",
            ".json",
            ".png",
            ".jpg",
            ".jpeg",
            ".gif",
            ".svg",
            ".ico",
            ".webp",
            ".woff",
            ".woff2",
            ".ttf",
            ".eot",
            ".txt",
            ".xml");

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpRequest  = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        if (path.startsWith("/api/") || path.equals("/api")) {
            chain.doFilter(request, response);
            return;
        }

        if (path.equals("/app") || path.equals("/app/")) {
            forwardToAppIndex(httpRequest, httpResponse);
            return;
        }

        if (path.startsWith("/app/")) {
            if (looksLikeStaticAsset(path)) {
                chain.doFilter(request, response);
                return;
            }

            forwardToAppIndex(httpRequest, httpResponse);
            return;
        }

        chain.doFilter(request, response);
    }

    private static boolean looksLikeStaticAsset(String path) {
        String lower = path.toLowerCase();
        for (String extension : STATIC_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static void forwardToAppIndex(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        RequestDispatcher dispatcher = request.getRequestDispatcher("/app/index.html");
        dispatcher.forward(request, response);
    }
}