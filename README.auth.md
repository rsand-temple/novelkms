# NovelKMS Stage 1 authentication overlay

This archive is an overlay for `rsand-temple/novelkms` `master` as inspected on 2026-06-15.
Copy `backend/` into `novelkms-backend/` and `frontend/` into `novelkms-frontend/`, preserving paths.

## Included

- `V9__authentication.sql` for H2 and PostgreSQL
- `app_user`, `user_identity`, `user_session`, `oauth_state`, and `pending_registration`
- Google and Meta authorization-code callbacks
- one-time OAuth state consumption
- opaque application sessions; only SHA-256 token hashes are stored
- `HttpOnly`, `SameSite=Lax`, configurable `Secure` cookies
- pending-registration cookie and registration transaction
- global authentication filter for existing `/api` endpoints
- React authentication gate, login page, and registration form

## Configuration

Merge `backend/config-auth-example.yaml` into the active Dropwizard configuration. Keep client secrets and database credentials in environment variables. The public URL must be HTTPS in hosted mode.

Register these exact callback URLs with the providers:

- `${PUBLIC_BASE_URL}/api/auth/google/callback`
- `${PUBLIC_BASE_URL}/api/auth/meta/callback`

Provider API versions are deliberately configuration, not Java constants. Confirm the currently supported Meta Graph API version when creating the Meta app and update the three Meta URLs together.

For local HTTP development only, set `secureCookies: false`. Hosted mode must leave it `true`.

## Apply

```bash
cp -R backend/src/main/java/com/richardsand/novelkms/* novelkms-backend/src/main/java/com/richardsand/novelkms/
cp -R backend/src/main/resources/db/migration/* novelkms-backend/src/main/resources/db/migration/
cp -R frontend/src/* novelkms-frontend/src/
```

Replace/merge `NovelKmsConfig.java`, `NovelKmsServer.java`, `frontend/src/main.jsx`, and `frontend/src/api/client.js` rather than blindly overwriting local uncommitted changes.

## Important Stage 1 boundary

The filter now requires a valid session for existing application APIs, but existing DAO queries are not yet tenant-scoped. That is Stage 2. Do not invite mutually untrusted users until `project.owner_user_id` and ownership-scoped DAO operations are complete.

## Recommended first test

1. Start PostgreSQL and apply migrations through normal server startup.
2. Configure only Google first; leave Meta absent.
3. Visit the application and verify redirect to login.
4. Complete Google OAuth and confirm the registration page receives a read-only email.
5. Register, refresh, and restart the server; verify the database-backed session remains valid.
6. Log out and confirm manuscript APIs return 401.
7. Repeat OAuth login; confirm it bypasses registration.
8. Attempt registration twice in two tabs; confirm unique constraints prevent duplicate identity/email creation.

## Review notes

- The archive uses the existing Apache HttpClient and `org.json` dependencies; no Maven dependency change is required.
- Meta does not expose the same `email_verified` claim as Google. This implementation treats an email returned by the authorized Meta `/me` request as provider-confirmed. Revisit that policy if Meta's app configuration or response semantics differ.
- Cleanup of expired OAuth states, pending registrations, and sessions should be added as a scheduled maintenance task after the vertical slice is running.
- CSRF hardening for state-changing application APIs belongs in deployment hardening, but should be completed before broad external exposure.
