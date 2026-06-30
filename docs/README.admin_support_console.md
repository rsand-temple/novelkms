# NovelKMS Admin Support Console — Implementation Memory

This document captures the admin-support implementation thread completed after the Stripe billing foundation. It is intended as the starting point for the next admin-functionality session.

## Current status

All changes from this thread have been committed to GitHub. The backend and frontend build successfully. The minimal admin support console is functional enough to search users, inspect user/billing/audit state, and grant family access.

## Backend implementation

### Role/security foundation

- Added `user_role` table through Flyway migrations for H2 and PostgreSQL.
- Added `Roles.ADMIN`.
- Added `NovelKmsPrincipal` implementing `Principal`, with immutable roles and `isInRole(...)` / `isAdmin()` helpers.
- `SessionService.authenticate(...)` now returns an authenticated session including both `AppUser` and roles.
- `AuthDao.findRolesForUser(UUID)` reads roles.
- `AuthenticationFilter` now creates the principal, stores request properties, and installs a JAX-RS `SecurityContext`.
- `NovelKmsServer` registers `RolesAllowedDynamicFeature`.
- `TenantAuthorizationFilter` and `SubscriptionAuthorizationFilter` handle `/admin/*` paths explicitly: admins pass, non-admins are denied before tenant checks.
- `/api/auth/status` returns `roles` in the authenticated user payload so the frontend can show admin navigation.

### Admin audit

- Added `admin_audit_log` table with admin user, target user, action, entity metadata, old/new values, reason, and timestamp.
- Added `AdminAuditLogEntry`, `AdminAuditDao`, and `AdminAuditResource`.
- Endpoints include recent audit, audit by target user, audit by admin user, and audit by id.
- Admin mutations should record old/new JSON and a human-readable reason.

### Admin user lookup

- Added admin DTOs for user summary/detail, subscription summary, and usage summary.
- Added `AdminUserDao` and `AdminUserResource`.
- Endpoints:
  - `GET /api/admin/users?query=&limit=`
  - `GET /api/admin/users/{userId}`
- Search supports blank recent users, email/name, UUID, Stripe customer id, and Stripe subscription id.
- Usage counts are intentionally approximate support metrics, filtered only on columns that exist in the real Flyway schema. Do not add `deleted_at` predicates unless the table actually has that column.

### Admin billing

- Added `GrantFamilyAccessRequest`.
- Added `AdminBillingDetail` response DTO.
- Added `AdminBillingService` and `AdminBillingResource`.
- Endpoints:
  - `GET /api/admin/billing/users/{userId}`
  - `POST /api/admin/billing/users/{userId}/family-access`
- `billingDetail(...)` returns subscription snapshot and computed flags: `hasSubscriptionRow`, `hasAccess`, `familyAccess`, `stripeLinked`, `trialActive`, `canceling`, `paymentProblem`, `accessReason`, `evaluatedAt`.
- `grantFamilyAccess(...)` requires an active target user, captures old subscription, calls `UserSubscriptionDao.setFamilyAccess(...)`, writes `GRANT_FAMILY_ACCESS` audit row, and returns the updated subscription.
- `family` remains a manual entitlement override that Stripe webhook updates should not demote.

## Frontend implementation

### Admin API client

Added `novelkms-frontend/src/api/admin.js` with helpers for user search, user detail, billing detail, grant family access, user audit, and recent audit.

### Admin console

Added `novelkms-frontend/src/components/admin/AdminSupportConsole.jsx`.

The console currently supports:

- `/admin` page branch in `App.jsx`.
- User search by email/name/user id/Stripe id.
- Selected user identity card with status and role chips.
- Billing card with access flags and subscription metadata.
- Usage counts.
- Audit list for the selected user.
- Grant family access dialog with reason/note.
- Back-to-workspace button.

The admin menu item is shown in `UserMenu.jsx` only when `auth.user.roles` includes `ADMIN`.

### UI fixes from this thread

- Fixed accidental `MenuItem` outside `Menu`, which caused `MUI: MenuListContext is missing`.
- Removed React/MUI dev warnings by:
  - using `slotProps` instead of old `primaryTypographyProps` / `secondaryTypographyProps`,
  - moving `Stack` layout props such as `alignItems` and `flexWrap` into `sx`,
  - avoiding synchronous effect-driven state resets where Eclipse/React lint complained.
- Adjusted role chip styling so `ADMIN` appears green/success like active status.

## Testing and build notes

- Added tests for principal/role behavior, auth roles, admin audit DAO, admin user DAO, and admin billing service.
- Moved DAO/service tests toward `NovelKmsTestBase` so Flyway initializes the real schema. This was important because hand-written mini schemas hid real column mismatches.
- `NovelKmsTestBase.truncateAll()` must delete `admin_audit_log`, `user_role`, and `user_subscription` before deleting `app_user`.
- Because the H2 test database is shared/static, test fixtures should use unique emails and unique Stripe ids.
- The whole project built successfully at the end of the thread.

## Important design decisions

- Admin architecture follows JAX-RS/J2EE-style roles rather than ad hoc checks.
- Admin mutation logic belongs in service classes, not directly in resources.
- Admin audit is mandatory for support mutations.
- The first mutation was deliberately narrow: grant family access.
- Revoke/remove family access is deferred until restoration semantics are designed.
- The admin console should remain a support workflow, not a broad dashboard, until the backend action model matures.

## Recommended next steps

1. Add `extendTrial(...)` as the next admin billing mutation. It should require a reason, validate the requested date/duration, preserve stronger entitlements such as `family`, and write old/new audit values.
2. Add a billing diagnostics panel: Stripe customer/subscription ids, webhook events related to that user, last failed webhook event, and local entitlement explanation.
3. Design manual override semantics before implementing revoke-family. Options include storing previous Stripe-derived status, querying Stripe live, falling back to `none`/`canceled`, or creating a richer manual override table.
4. Add resource/security tests for admin endpoints: unauthenticated = 401, authenticated non-admin = 403, admin = allowed.
5. Consider adding an admin-only navigation guard page state, but keep relying on backend enforcement as the authority.

## Smoke-test workflow

1. Sign in as an admin user.
2. Open `/admin`.
3. Search for a user by email or Stripe id.
4. Confirm user, billing, usage, and audit cards load.
5. Grant family access with reason/note.
6. Confirm billing shows `family` and access is true.
7. Confirm an audit row appears for `GRANT_FAMILY_ACCESS`.
