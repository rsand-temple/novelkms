package com.richardsand.novelkms.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.richardsand.novelkms.NovelKmsConfig;
import com.richardsand.novelkms.dao.AuthDao;
import com.richardsand.novelkms.model.AppUser;

public class SessionService {
    private final AuthDao dao;
    private final NovelKmsConfig.Auth config;
    public SessionService(AuthDao dao, NovelKmsConfig.Auth config) { this.dao = dao; this.config = config; }
    public String create(AppUser user, String ip, String userAgent) throws Exception {
        String raw = CryptoTokens.randomToken(32);
        dao.createSession(CryptoTokens.sha256(raw), user.id(), Instant.now().plus(Duration.ofDays(config.sessionDays)), ip, userAgent);
        return raw;
    }
    public Optional<AppUser> authenticate(String raw) throws Exception {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String hash = CryptoTokens.sha256(raw);
        Optional<AuthDao.SessionUser> result = dao.findSessionUser(hash);
        if (result.isPresent()) dao.touchSession(hash);
        return result.map(AuthDao.SessionUser::user);
    }
    public void revoke(String raw) throws Exception { if (raw != null) dao.revokeSession(CryptoTokens.sha256(raw)); }
}
