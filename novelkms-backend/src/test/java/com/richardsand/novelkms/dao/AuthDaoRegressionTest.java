package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.auth.OAuthProfile;
import com.richardsand.novelkms.model.AppUser;
import com.richardsand.novelkms.support.DatabaseIntegrationTest;

class AuthDaoRegressionTest extends DatabaseIntegrationTest {

    @Test
    void oauthStateIsSingleUse() throws Exception {
        AuthDao dao = new AuthDao(dataSource);
        dao.createOAuthState("state-hash", "GOOGLE", "/", Instant.now().plusSeconds(60));

        assertTrue(dao.consumeOAuthState("state-hash").isPresent());
        assertTrue(dao.consumeOAuthState("state-hash").isEmpty());
    }

    @Test
    void expiredOauthStateIsRejectedAndConsumed() throws Exception {
        AuthDao dao = new AuthDao(dataSource);
        dao.createOAuthState("expired-state", "GOOGLE", "/", Instant.now().minusSeconds(1));

        assertTrue(dao.consumeOAuthState("expired-state").isEmpty());
        assertTrue(dao.consumeOAuthState("expired-state").isEmpty());
    }

    @Test
    void pendingRegistrationIsSingleUseAfterRegistration() throws Exception {
        AuthDao dao = new AuthDao(dataSource);
        OAuthProfile profile = new OAuthProfile("GOOGLE", "subject-1", "writer@example.com", true, "First", "Last");
        dao.createPendingRegistration("pending-hash", profile, Instant.now().plusSeconds(60));

        AuthDao.PendingRegistration pending = dao.findPendingRegistration("pending-hash").orElseThrow();
        AppUser user = dao.register(pending, "First", "Last", "Writer", null);

        assertNotNull(user.id());
        assertTrue(dao.findPendingRegistration("pending-hash").isEmpty());
        assertEquals(user.id(), dao.findUserByIdentity("GOOGLE", "subject-1").orElseThrow().id());
    }

    @Test
    void normalizedEmailCannotBeRegisteredTwice() throws Exception {
        AuthDao dao = new AuthDao(dataSource);
        register(dao, "subject-a", "Writer@Example.com", "Writer A");

        SQLException error = assertThrows(SQLException.class,
                () -> register(dao, "subject-b", "writer@example.com", "Writer B"));
        assertNotNull(error.getMessage());
    }

    @Test
    void sessionSurvivesDaoRecreationUntilRevoked() throws Exception {
        AuthDao dao = new AuthDao(dataSource);
        AppUser user = register(dao, "session-subject", "session@example.com", "Session User");
        String tokenHash = "session-" + UUID.randomUUID();
        dao.createSession(tokenHash, user.id(), Instant.now().plusSeconds(3600), "127.0.0.1", "JUnit");

        assertEquals(user.id(), new AuthDao(dataSource).findSessionUser(tokenHash).orElseThrow().user().id());
        dao.revokeSession(tokenHash);
        assertTrue(new AuthDao(dataSource).findSessionUser(tokenHash).isEmpty());
    }

    @Test
    void expiredSessionDoesNotAuthenticate() throws Exception {
        AuthDao dao = new AuthDao(dataSource);
        AppUser user = register(dao, "expired-session-subject", "expired@example.com", "Expired User");
        dao.createSession("expired-session", user.id(), Instant.now().minusSeconds(1), null, null);
        assertTrue(dao.findSessionUser("expired-session").isEmpty());
    }

    private AppUser register(AuthDao dao, String subject, String email, String displayName) throws Exception {
        String hash = "pending-" + UUID.randomUUID();
        dao.createPendingRegistration(hash,
                new OAuthProfile("GOOGLE", subject, email, true, null, null),
                Instant.now().plusSeconds(60));
        return dao.register(dao.findPendingRegistration(hash).orElseThrow(), null, null, displayName, null);
    }
}
