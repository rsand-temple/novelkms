package com.richardsand.novelkms.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.richardsand.novelkms.NovelKmsConfig;
import com.richardsand.novelkms.dao.AuthDao;
import com.richardsand.novelkms.model.AppUser;
import com.richardsand.novelkms.utils.EmailNormalizer;

public class OAuthService implements AutoCloseable {
    public record CallbackResult(AppUser user, String pendingRegistrationToken, String returnPath) {
    }

    private final NovelKmsConfig.Auth auth;
    private final AuthDao             dao;
    private final CloseableHttpClient http = HttpClients.createDefault();

    public OAuthService(NovelKmsConfig.Auth auth, AuthDao dao) {
        this.auth = auth;
        this.dao = dao;
    }

    public URI begin(String providerName, String returnPath) throws Exception {
        NovelKmsConfig.OAuthProvider p     = provider(providerName);
        String                       state = CryptoTokens.randomToken(32);
        dao.createOAuthState(CryptoTokens.sha256(state), providerName.toUpperCase(Locale.ROOT), safeReturnPath(returnPath), Instant.now().plus(Duration.ofMinutes(10)));
        return new URIBuilder(p.authorizationUrl)
                .addParameter("client_id", p.clientId)
                .addParameter("redirect_uri", callbackUrl(providerName))
                .addParameter("response_type", "code")
                .addParameter("scope", p.scope)
                .addParameter("state", state)
                .build();
    }

    public CallbackResult callback(String providerName, String code, String state) throws Exception {
        AuthDao.OAuthState oauthState = dao.consumeOAuthState(CryptoTokens.sha256(state))
                .orElseThrow(() -> new IllegalArgumentException("OAuth state is invalid or expired"));
        String             provider   = providerName.toUpperCase(Locale.ROOT);
        if (!provider.equals(oauthState.provider()))
            throw new IllegalArgumentException("OAuth provider does not match state");

        OAuthProfile      profile  = exchange(providerName, code);
        Optional<AppUser> existing = dao.findUserByIdentity(profile.provider(), profile.subject());
        if (existing.isPresent()) {
            dao.touchLogin(existing.get().id(), profile.provider(), profile.subject());
            return new CallbackResult(existing.get(), null, oauthState.returnPath());
        }
        if (profile.email() == null || profile.email().isBlank())
            throw new IllegalArgumentException("OAuth provider did not return an email address");
        if (!profile.emailVerified())
            throw new IllegalArgumentException("OAuth provider did not verify the email address");
        if (dao.normalizedEmailExists(EmailNormalizer.normalize(profile.email()))) {
            throw new IllegalStateException("An account already exists for this email. Sign in with its existing provider.");
        }
        String pendingToken = CryptoTokens.randomToken(32);
        dao.createPendingRegistration(CryptoTokens.sha256(pendingToken), profile, Instant.now().plus(Duration.ofMinutes(20)));
        return new CallbackResult(null, pendingToken, oauthState.returnPath());
    }

    private OAuthProfile exchange(String providerName, String code) throws Exception {
        NovelKmsConfig.OAuthProvider p = provider(providerName);

        String body = "client_id=" + enc(p.clientId) + "&client_secret=" + enc(p.clientSecret)
                + "&code=" + enc(code) + "&redirect_uri=" + enc(callbackUrl(providerName))
                + "&grant_type=authorization_code";

        HttpPost post = new HttpPost(p.tokenUrl);
        post.setHeader("Accept", "application/json");
        post.setEntity(new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED));

        JSONObject token       = executeJson(post);
        String     accessToken = token.getString("access_token");

        URIBuilder userInfo = new URIBuilder(p.userInfoUrl);
        if (providerName.equalsIgnoreCase("meta")) {
            userInfo.addParameter("fields", "id,email,first_name,last_name").addParameter("access_token", accessToken);
        }
        HttpGet get = new HttpGet(userInfo.build());
        get.setHeader("Accept", "application/json");
        get.setHeader("Authorization", "Bearer " + accessToken);
        JSONObject profile = executeJson(get);
        if (providerName.equalsIgnoreCase("google")) {
            return new OAuthProfile(
                    "GOOGLE",
                    profile.getString("sub"),
                    profile.optString("email", null),
                    profile.optBoolean("email_verified", false),
                    profile.optString("given_name", null),
                    profile.optString("family_name", null));
        }
        if (providerName.equalsIgnoreCase("github")) {
            return githubProfile(profile, accessToken);
        }
        if (providerName.equalsIgnoreCase("meta")) {
            return new OAuthProfile(
                    "META",
                    profile.getString("id"),
                    profile.optString("email", null),
                    true,
                    profile.optString("first_name", null),
                    profile.optString("last_name", null));
        }
        throw new IllegalArgumentException("OAuth provider is not configured: " + providerName);
    }

    private JSONObject executeJson(org.apache.http.client.methods.HttpUriRequest request) throws IOException {
        try (CloseableHttpResponse response = http.execute(request)) {
            String body   = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            int    status = response.getStatusLine().getStatusCode();

            if (status / 100 != 2) {
                throw new IOException("OAuth endpoint returned " + status
                        + " for " + request.getMethod() + " " + request.getURI()
                        + ": " + truncateForLog(body, 1000));
            }

            String trimmed = body == null ? "" : body.trim();
            if (!trimmed.startsWith("{")) {
                throw new IOException("OAuth endpoint did not return JSON for "
                        + request.getMethod() + " " + request.getURI()
                        + ": " + truncateForLog(trimmed, 1000));
            }

            return new JSONObject(trimmed);
        }
    }

    private static String truncateForLog(String value, int max) {
        if (value == null)
            return "";
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private NovelKmsConfig.OAuthProvider provider(String name) {
        NovelKmsConfig.OAuthProvider p = name.equalsIgnoreCase("google") ? auth.google : name.equalsIgnoreCase("github") ? auth.github : name.equalsIgnoreCase("meta") ? auth.meta : null;
        if (p == null || p.clientId == null || p.clientSecret == null)
            throw new IllegalArgumentException("OAuth provider is not configured: " + name);
        return p;
    }

    private String callbackUrl(String providerName) {
        return auth.publicBaseUrl.replaceAll("/$", "") + "/api/auth/" + providerName.toLowerCase(Locale.ROOT) + "/callback";
    }

    private static String safeReturnPath(String value) {
        return value != null && value.startsWith("/") && !value.startsWith("//") ? value : "/";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws Exception {
        http.close();
    }

    private OAuthProfile githubProfile(JSONObject profile, String accessToken) throws Exception {
        String  subject  = String.valueOf(profile.get("id"));
        String  email    = profile.optString("email", null);
        boolean verified = false;

        if (email != null && !email.isBlank()) {
            verified = true; // GitHub's /user email is generally usable, but /user/emails is better.
        }

        if (email == null || email.isBlank() || !verified) {
            HttpGet emailsGet = new HttpGet("https://api.github.com/user/emails");
            emailsGet.setHeader("Authorization", "Bearer " + accessToken);
            emailsGet.setHeader("Accept", "application/vnd.github+json");

            JSONArray emails = executeJsonArray(emailsGet);

            for (int i = 0; i < emails.length(); i++) {
                JSONObject e = emails.getJSONObject(i);
                if (e.optBoolean("primary", false) && e.optBoolean("verified", false)) {
                    email = e.optString("email", null);
                    verified = email != null && !email.isBlank();
                    break;
                }
            }
        }

        String name  = profile.optString("name", null);
        String login = profile.optString("login", null);

        // GitHub doesn't give reliable first/last names.
        // You could use login as suggested display name later, but OAuthProfile doesn't currently carry displayName.
        return new OAuthProfile("GITHUB", subject, email, verified, login, name);
    }

    private JSONArray executeJsonArray(HttpUriRequest request) throws IOException {
        try (CloseableHttpResponse response = http.execute(request)) {
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (response.getStatusLine().getStatusCode() / 100 != 2)
                throw new IOException("OAuth endpoint returned " + response.getStatusLine().getStatusCode());
            return new JSONArray(body);
        }
    }
}
