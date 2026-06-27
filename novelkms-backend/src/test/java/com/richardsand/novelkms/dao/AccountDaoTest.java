package com.richardsand.novelkms.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.richardsand.novelkms.NovelKmsTestBase;
import com.richardsand.novelkms.dao.AccountDao.Account;

class AccountDaoTest extends NovelKmsTestBase {

    private AccountDao accountDao;

    @BeforeEach
    void setUp() throws SQLException {
        truncateAll();
        accountDao = new AccountDao(ds);
    }

    @Test
    void getAccount_knownUser_returnsAccount() throws SQLException {
        Optional<Account> account = accountDao.getAccount(TEST_USER_ID);

        assertTrue(account.isPresent());
        assertEquals("test.user@example.com", account.get().email());
        assertNull(account.get().first_name());
        assertNull(account.get().last_name());
        assertEquals("Test User", account.get().display_name());
        assertNull(account.get().mobile_number());
        assertNotNull(account.get().created_at());
        assertNull(account.get().last_login_at());
    }

    @Test
    void getAccount_unknownUser_returnsEmpty() throws SQLException {
        Optional<Account> account = accountDao.getAccount(UUID.randomUUID());

        assertFalse(account.isPresent());
    }

    @Test
    void updateAccount_knownUser_updatesEditableFieldsOnly() throws SQLException {
        Optional<Account> updated = accountDao.updateAccount(
                TEST_USER_ID,
                "Richard",
                "Sand",
                "Richard Sand",
                "555-111-2222");
        

        assertTrue(updated.isPresent());
        assertEquals("test.user@example.com", updated.get().email());
        assertEquals("Richard", updated.get().first_name());
        assertEquals("Sand", updated.get().last_name());
        assertEquals("Richard Sand", updated.get().display_name());
        assertEquals("555-111-2222", updated.get().mobile_number());
    }

    @Test
    void updateAccount_trimsValuesAndConvertsBlanksToNull() throws SQLException {
        Optional<Account> updated = accountDao.updateAccount(
                TEST_USER_ID,
                "  Richard  ",
                "   ",
                "  Richard Sand  ",
                "");

        assertTrue(updated.isPresent());
        assertEquals("Richard", updated.get().first_name());
        assertNull(updated.get().last_name());
        assertEquals("Richard Sand", updated.get().display_name());
        assertNull(updated.get().mobile_number());
    }

    @Test
    void updateAccount_unknownUser_returnsEmpty() throws SQLException {
        Optional<Account> updated = accountDao.updateAccount(
                UUID.randomUUID(),
                "Ghost",
                "User",
                "Ghost User",
                "555-000-0000");

        assertFalse(updated.isPresent());
    }

    @Test
    void delete_knownUserAndEmail_deletesUser() throws SQLException {
        boolean deleted = accountDao.delete(TEST_USER_ID, "TEST.USER@EXAMPLE.COM");

        assertTrue(deleted);
        assertFalse(accountDao.getAccount(TEST_USER_ID).isPresent());
    }

    @Test
    void delete_wrongEmail_returnsFalseAndLeavesUser() throws SQLException {
        boolean deleted = accountDao.delete(TEST_USER_ID, "someone.else@example.com");

        assertFalse(deleted);
        assertTrue(accountDao.getAccount(TEST_USER_ID).isPresent());
    }
}
