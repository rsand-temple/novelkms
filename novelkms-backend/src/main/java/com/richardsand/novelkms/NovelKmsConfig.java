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

    @JsonProperty
    Database database;

    @JsonProperty
    Auth auth;

    @JsonProperty
    Security security;
}
