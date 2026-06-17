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

# NovelKMS Stage 2 — Project ownership and tenant authorization

This overlay assumes Stage 1 authentication is already installed and working.

## What this stage does

- Adds `project.owner_user_id` in Flyway V10.
- Assigns existing projects automatically when exactly one `app_user` exists.
- Makes project list/get/create/update/delete/word-count operations user-scoped.
- Adds `TenantAccessDao` ownership checks for projects, books, parts, chapters, and scenes.
- Adds an authorization filter after the Stage 1 authentication filter.
- Checks UUIDs in API paths before resources execute.
- Checks UUID-bearing JSON payloads used by reorder, move, and chapter creation requests.
- Explicitly checks the multipart `projectId` before DOCX import.
- Returns 404 for another user's object so object existence is not disclosed.
- Blocks `/api/admin/*` for ordinary users.
- Temporarily makes application-global templates and styles read-only. Book/project overrides remain usable.
- Replaces `/register` with `/` after successful registration.

## Apply

Copy the contents of `backend/` over `novelkms-backend/` and `frontend/` over `novelkms-frontend/`.

The important replacements are:

- `ProjectDao.java`
- `ProjectResource.java`
- `ImportResource.java`
- `NovelKmsServer.java`
- `RegistrationPage.jsx`

The new files are:

- `CurrentUser.java`
- `TenantAuthorizationFilter.java`
- `TenantAccessDao.java`
- both V10 migration files

Then run:

```bash
mvn clean test
mvn package
```

Restart Dropwizard so Flyway applies V10.

## Verify migration

After startup, your existing projects should still be visible. If none are visible, the V10 migration probably ran when the database had either zero or more than one users, leaving legacy projects unclaimed.

For the present one-user H2 database, stop NovelKMS and run H2's Shell tool using the same H2 version as the backend:

```bash
java -cp ~/.m2/repository/com/h2database/h2/2.4.240/h2-2.4.240.jar \
  org.h2.tools.Shell \
  -url 'jdbc:h2:/absolute/path/to/your/database-file' \
  -user sa
```

Use the exact URL, username, and password from your Dropwizard YAML. Do not include the `.mv.db` suffix in the JDBC path.

Useful queries:

```sql
SELECT id, email_address, display_name FROM app_user;
SELECT id, title, owner_user_id FROM project;
SELECT COUNT(*) FROM user_identity;
SELECT COUNT(*) FROM user_session WHERE revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP;
```

To claim orphaned projects when there is exactly one intended owner:

```sql
UPDATE project
SET owner_user_id = (SELECT id FROM app_user ORDER BY created_at FETCH FIRST 1 ROW ONLY)
WHERE owner_user_id IS NULL;
```

## Multi-user isolation test

1. Register a second Google account as a test user.
2. The second user should see an empty project list.
3. Create one project as the second user.
4. Copy a project, book, chapter, or scene UUID from the first user's browser session.
5. Request that UUID while signed in as the second user. The response should be 404.
6. Verify that an export URL copied from the first account also returns 404 to the second account.
7. Verify that DOCX import refuses a project ID owned by the other account.

## Important scope boundary

This stage protects all current project-rooted HTTP access and makes the project root owner-aware. It deliberately does not yet create USER-scoped template/style defaults. Existing factory globals are therefore read-only for authenticated users; per-book and per-project overrides continue to work.

The next security pass should:

- replace GLOBAL template/style editing with `SYSTEM -> USER -> BOOK/PROJECT` resolution;
- remove or replace remaining internal unscoped DAO overloads after services accept a user ID directly;
- introduce an administrator role for maintenance endpoints;
- add integration tests that run two users against every resource class;
- enforce `project.owner_user_id NOT NULL` after confirming there are no orphan rows.

## Notes on the authorization filter

The filter is intentionally defense-in-depth. ProjectResource itself uses tenant-scoped SQL, while path-based child resources are checked before their existing DAO methods execute. Move/reorder payloads are buffered, validated, and restored before Jersey deserializes them.

This overlay was prepared against the current public `master` files plus the Stage 1 overlay. It could not be compiled against your locally modified tree, so resolve any local drift rather than overwriting newer edits blindly.

# NovelKMS Stage 3 — Per-user template and style defaults

This overlay assumes Stages 1 and 2 are already applied.

## Resulting cascades

- Templates: `BOOK -> USER -> SYSTEM`
- Styles: `BOOK -> PROJECT -> USER -> SYSTEM`

The existing frontend URLs containing `/global/` are intentionally retained for compatibility. They now mean "my user default", not an application-wide mutable global.

## Apply

Copy `backend/src/main/...` into `novelkms-backend/src/main/...`.

Then update `NovelKmsServer.java`:

1. Add:

```java
import com.richardsand.novelkms.dao.UserStyleDao;
```

2. Replace:

```java
StyleDao styleDao = new StyleDao(ds);
```

with:

```java
UserStyleDao userStyleDao = new UserStyleDao(ds);
```

3. In the HK2 binder replace:

```java
bind(styleDao).to(StyleDao.class);
```

with:

```java
bind(userStyleDao).to(UserStyleDao.class);
```

`TemplateDao` keeps the same class/binding. `ExportService` remains source-compatible because the replacement DAO includes the original `resolveForBook(UUID, String)` signature and derives the owning user through the book/project hierarchy.

## Migration behavior

V11 converts existing `GLOBAL` rows into immutable `SYSTEM` rows. No user rows are eagerly copied. A user sees SYSTEM until making an edit; the first edit creates a USER row. Reset deletes the USER row, exposing SYSTEM again.

Book and project overrides are left unchanged and remain owned through their parent hierarchy.

## Frontend

No frontend API changes are required. The current global template/style editor now edits the signed-in user's defaults. Returned `scope` values will be `SYSTEM`, `USER`, `PROJECT`, or `BOOK`.

Any UI text saying “Global” should eventually be changed to “My Defaults”; this is cosmetic and is not required for Stage 3 to work.

## Verification

1. Sign in as user A and edit the cover default.
2. Confirm an un-overridden book for user A changes.
3. Sign in as user B and confirm the factory cover remains unchanged.
4. Edit user B's cover default and confirm user A is unaffected.
5. Create a book override, then change the user default; the book override must remain unchanged.
6. Reset the book override; it should reveal the USER default.
7. Reset the USER default; it should reveal SYSTEM.

Database checks:

```sql
SELECT template_type, scope, user_id, book_id FROM template ORDER BY template_type, scope;
SELECT style_key, scope, user_id, project_id, book_id FROM style ORDER BY style_key, scope;
```

## Important

Do not delete SYSTEM rows. They are the factory fallback. Normal application endpoints never mutate them.
