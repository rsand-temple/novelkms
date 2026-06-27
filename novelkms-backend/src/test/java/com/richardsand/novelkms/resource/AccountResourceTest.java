package com.richardsand.novelkms.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.AccountDao;
import com.richardsand.novelkms.dao.AccountDao.Account;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ExtendWith(DropwizardExtensionsSupport.class)
class AccountResourceTest extends NovelKmsTestBase {

    private static final AccountDao ACCOUNT_DAO = new AccountDao(ds);

    static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addProvider(testAuthenticationFilter())
            .addResource(new AccountResource(ACCOUNT_DAO))
            .setMapper(createMapper())
            .build();

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
    }

    @Test
    void getAccount_returnsAuthenticatedUsersAccount() {
        Response r = RESOURCES.target("/account").request().get();

        assertEquals(200, r.getStatus());
        Account account = r.readEntity(Account.class);
        assertEquals("test.user@example.com", account.email());
        assertEquals("Test User", account.display_name());
    }

    @Test
    void getAccount_missingUser_returns404() throws SQLException {
        assertTrue(ACCOUNT_DAO.delete(TEST_USER_ID, "test.user@example.com"));

        Response r = RESOURCES.target("/account").request().get();

        assertEquals(404, r.getStatus());
    }

    @Test
    void putAccount_updatesAuthenticatedUsersEditableFields() {
        Response r = RESOURCES.target("/account")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of(
                        "firstname", "Richard",
                        "lastname", "Sand",
                        "displayname", "Richard Sand",
                        "mobile", "555-111-2222")));

        assertEquals(200, r.getStatus());
        Account account = r.readEntity(Account.class);
        assertEquals("test.user@example.com", account.email());
        assertEquals("Richard", account.first_name());
        assertEquals("Sand", account.last_name());
        assertEquals("Richard Sand", account.display_name());
        assertEquals("555-111-2222", account.mobile_number());
    }

    @Test
    void putAccount_blankValuesBecomeNull() {
        Response r = RESOURCES.target("/account")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of(
                        "firstname", "   ",
                        "lastname", "   ",
                        "displayname", "Thing",
                        "mobile", "   ")));

        assertEquals(200, r.getStatus());
        Account account = r.readEntity(Account.class);
        assertNull(account.first_name());
        assertNull(account.last_name());
        assertNull(account.mobile_number());
    }

    @Test
    void putAccount_missingUser_returns404() throws SQLException {
        assertTrue(ACCOUNT_DAO.delete(TEST_USER_ID, "test.user@example.com"));

        Response r = RESOURCES.target("/account")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of(
                        "firstname", "Richard",
                        "lastname", "Sand",
                        "displayname", "Richard Sand",
                        "mobile", "555-111-2222")));

        assertEquals(404, r.getStatus());
    }

    @Test
    void postAccount_formEncoded_updatesAuthenticatedUsersEditableFields() {
        Response r = RESOURCES.target("/account")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(Map.of(
                        "firstname", "Richard",
                        "lastname", "Sand",
                        "displayname", "Richard Sand",
                        "mobile", "555-111-2222")));
        
        assertEquals(200, r.getStatus());
        Account account = r.readEntity(Account.class);
        assertEquals("Richard", account.first_name());
        assertEquals("Sand", account.last_name());
        assertEquals("Richard Sand", account.display_name());
        assertEquals("555-111-2222", account.mobile_number());
    }
}
