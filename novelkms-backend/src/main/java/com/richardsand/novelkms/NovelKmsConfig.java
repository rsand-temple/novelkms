package com.richardsand.novelkms;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.dropwizard.core.Configuration;
import lombok.Getter;

@Getter
public class NovelKmsConfig extends Configuration {
    @Getter
    public static class Database {
        public String url;
        public String adminUser;
        public String adminPwd;
    }

    @Getter
    public static class OAuthProvider {
        public String clientId;
        public String clientSecret;
        public String authorizationUrl;
        public String tokenUrl;
        public String userInfoUrl;
        public String scope;
        public String tenant = null;   // for MS

        // Sign in with Apple uses a generated ES256 JWT as client_secret.
        // These fields hold the material needed to generate it server-side.
        public String teamId;
        public String keyId;
        public String privateKey;
    }

    @Getter
    public static class Auth {
        public String        publicBaseUrl;
        public String        frontendBaseUrl = "";
        public boolean       secureCookies   = true;
        public int           sessionDays     = 30;
        public OAuthProvider google;
        public OAuthProvider meta;
        public OAuthProvider github;
        public OAuthProvider microsoft;
        public OAuthProvider apple;
    }

    @Getter
    public static class Security {
        /**
         * Master key for encrypting secrets at rest (BYOK AI provider API keys).
         * Normally injected from the NOVELKMS_ENCRYPTION_KEY environment variable.
         * A Base64 16/24/32-byte key is used directly; any other non-blank value
         * is treated as a passphrase (SHA-256 derived). Blank uses an INSECURE
         * development key — override before storing real secrets.
         */
        public String encryptionKey;
    }

    @Getter
    public static class Billing {
        public String  stripeWebhookSecret;
        public String  stripeSecretKey;
        public String  stripePriceId;
        public String  successUrl;
        public String  cancelUrl;
        public boolean enforceSubscriptions = false;
    }

    @Getter
    public static class Tools {
        public Weather weather = new Weather();

        @Getter
        public static class Weather {
            public boolean enabled  = true;
            public String  provider = "open-meteo";
        }
    }

    /**
     * Artifacts (non-manuscript project file store). {@code storageDir} is the
     * host-mounted directory the blob bytes live in; blank falls back to a temp
     * directory with a startup warning (local dev only). Quotas are per-user:
     * {@code defaultUserQuotaBytes} applies unless a user has an
     * {@code artifact_quota_bytes} override.
     */
    @Getter
    public static class Artifacts {
        public boolean enabled               = true;
        public String  storageDir;
        public long    maxFileSizeBytes      = 52_428_800L;     // 50 MB
        public long    defaultUserQuotaBytes = 1_073_741_824L;  // 1 GB
    }

    @JsonProperty
    Database database;

    @JsonProperty
    Auth auth;

    @JsonProperty
    Security security;

    @JsonProperty
    Billing billing;

    @JsonProperty
    Tools tools = new Tools();

    @JsonProperty
    Artifacts artifacts = new Artifacts();
}
