# NovelKMS PostgreSQL Migration

## Purpose

This document records the successful migration of NovelKMS from H2 to PostgreSQL on the Fedora media server, plus the deployment, logging, and operational fixes completed during the same thread.

This thread began after the following infrastructure was already complete:

- NovelKMS and Caddy were running as rootless Podman Quadlet services.
- Public HTTPS access worked through Caddy.
- Google OAuth worked.
- Reboot persistence was verified.
- Dropbox backup scheduling had been planned/configured.
- The server-side H2 database was known to be disposable.
- The authoritative data source was the local Eclipse H2 database containing the user's real NovelKMS projects and manuscripts.

## High-Level Outcome

Completed successfully:

- PostgreSQL now runs as a rootless Podman/Quadlet service.
- NovelKMS now connects to PostgreSQL instead of H2.
- Flyway successfully initialized the PostgreSQL schema.
- The local Eclipse H2 database was migrated into PostgreSQL.
- The migrated data includes the real projects, books, chapters, scenes, templates, styles, images, and related content.
- The public NovelKMS site works again through Caddy.

## PostgreSQL Service

### PostgreSQL container

PostgreSQL was added as a rootless Podman Quadlet service.

The container image used:

```text
docker.io/library/postgres:17
```

The service name:

```text
postgres.service
```

The container name:

```text
novelkms-postgres
```

Database values used:

```text
POSTGRES_DB=novelkmsdb
POSTGRES_USER=novelkms
POSTGRES_PASSWORD=<stored in novelkms.env>
```

Important lesson:

The first health-check attempt used `novelkms` as the database name, but the env file had `novelkmsdb`. PostgreSQL had initialized correctly; the health check was simply probing the wrong database.

Correct health check:

```ini
HealthCmd=pg_isready -U novelkms -d novelkmsdb
```

### PostgreSQL data persistence

A Podman-managed volume was preferred over a direct bind mount for PostgreSQL data because rootless PostgreSQL file ownership can be fussy with host bind mounts.

A Quadlet `.volume` unit was created:

```text
/home/rsand/.config/containers/systemd/postgres-data.volume
```

Conceptual contents:

```ini
[Unit]
Description=Persistent PostgreSQL data for NovelKMS

[Volume]
VolumeName=novelkms-postgres-data
```

The PostgreSQL container mounts it at:

```text
/var/lib/postgresql/data
```

### Private container network

A private Podman network was created for NovelKMS, PostgreSQL, and Caddy:

```text
novelkms.network
```

File:

```text
/home/rsand/.config/containers/systemd/novelkms.network
```

Conceptual contents:

```ini
[Unit]
Description=Private network for NovelKMS services

[Network]
NetworkName=novelkms
```

PostgreSQL does not publish port `5432` publicly.

For migration only, a temporary localhost-only port was added:

```ini
PublishPort=127.0.0.1:15432:5432
```

This allowed the desktop migration tool to reach PostgreSQL through an SSH tunnel:

```text
localhost:15432 -> media server 127.0.0.1:15432 -> postgres container:5432
```

After migration, this temporary port should be removed unless still needed for maintenance.

## NovelKMS PostgreSQL Configuration

The server-side `config.yaml` database block was changed from H2 to PostgreSQL.

Essential shape:

```yaml
database:
  driverClass: org.postgresql.Driver
  url: jdbc:postgresql://novelkms-postgres:5432/${POSTGRES_DB}
  user: ${POSTGRES_USER}
  password: ${POSTGRES_PASSWORD}
```

Important notes:

- The hostname is the PostgreSQL container name: `novelkms-postgres`.
- Do not use `localhost` inside the NovelKMS container; that would refer to the NovelKMS container itself.
- PostgreSQL credentials remain in `novelkms.env`, not in `config.yaml`.
- The `/data` mount from the old H2 deployment can remain temporarily for rollback convenience, but PostgreSQL no longer uses it.

The NovelKMS Quadlet was attached to the private network:

```ini
Network=novelkms.network
```

NovelKMS was also ordered after PostgreSQL:

```ini
Requires=postgres.service
After=postgres.service
```

## Caddy Networking Fix

Once NovelKMS moved onto the private Podman network, Caddy was also attached to that same network.

Caddyfile changed from:

```caddyfile
novelkms.richardsand.com {
    reverse_proxy host.containers.internal:8080
}
```

to:

```caddyfile
novelkms.richardsand.com {
    reverse_proxy novelkms:8080
}
```

Caddy Quadlet gained:

```ini
Network=novelkms.network
```

This allows Caddy to proxy directly to the NovelKMS container by name.

### Caddy random stopping issue

Caddy was later observed stopping cleanly with SIGTERM around 04:36 AM:

```text
systemd: Stopping caddy.service
caddy: shutting down apps, then terminating
caddy: shutdown complete
systemd: Stopped caddy.service
```

This was not a crash. Systemd deliberately stopped it.

Likely cause: the Caddy unit still had a hard dependency on NovelKMS:

```ini
Requires=novelkms.service
After=novelkms.service
```

Recommended fix:

```ini
Wants=novelkms.service
After=novelkms.service
```

or only:

```ini
After=novelkms.service
```

Rationale:

- Caddy should not be tightly bound to NovelKMS lifecycle.
- During app deploy/restart, Caddy can stay running and temporarily serve a 502.
- Restarting NovelKMS should not tear down the HTTPS reverse proxy.

## H2 to PostgreSQL Data Migration

### Source of truth

The server H2 database was blank except for a sign-in record and was considered throwaway.

The authoritative source was the local Eclipse H2 database containing the real NovelKMS data.

The migrated data was verified to include:

```text
93 chapters
442 scenes
```

and all expected projects/books/content existed in PostgreSQL.

### Migration approach

A standalone Java migration utility was created.

Initial implementation used a custom argument parser; it was later refactored to use Apache Commons CLI.

The migrator was packaged as a Maven project:

```text
novelkms-db-migrator
```

It uses:

- Java 17
- H2 JDBC driver
- PostgreSQL JDBC driver
- Apache Commons CLI

### Migrator behavior

The migration tool:

- Opens H2 read-only.
- Connects to PostgreSQL via JDBC.
- Assumes Flyway has already created the PostgreSQL schema.
- Excludes `flyway_schema_history`.
- Discovers matching tables and columns case-insensitively.
- Orders inserts based on PostgreSQL foreign keys.
- Truncates PostgreSQL application tables with `RESTART IDENTITY CASCADE`.
- Requires `--allow-nonempty-target` if target tables are non-empty.
- Copies all rows in one PostgreSQL transaction.
- Converts BLOB/CLOB/UUID/JSON/timestamp values explicitly.
- Compares source and target row counts.
- Supports `--dry-run`, which performs the full copy and verification but rolls back.

PowerShell dry run shape:

```powershell
$env:NOVELKMS_PG_PASSWORD = 'your-postgres-password'

java -jar target\novelkms-db-migrator-1.0.0.jar `
  --h2-url 'jdbc:h2:file:C:/Backups/NovelKMS/novelkms' `
  --pg-url 'jdbc:postgresql://localhost:15432/novelkmsdb' `
  --pg-user novelkms `
  --allow-nonempty-target `
  --dry-run
```

Real migration shape:

```powershell
java -jar target\novelkms-db-migrator-1.0.0.jar `
  --h2-url 'jdbc:h2:file:C:/Backups/NovelKMS/novelkms' `
  --pg-url 'jdbc:postgresql://localhost:15432/novelkmsdb' `
  --pg-user novelkms `
  --allow-nonempty-target
```

Important H2 URL note:

If the file is:

```text
C:\Backups\NovelKMS\novelkms.mv.db
```

the JDBC URL is:

```text
jdbc:h2:file:C:/Backups/NovelKMS/novelkms
```

Do not include `.mv.db`.

### User/account tables

The user-related tables were:

```text
app_user
user_identity
user_session
```

Columns containing user references:

```text
project.owner_user_id
style.user_id
template.user_id
user_identity.user_id
user_session.user_id
user_session.user_agent
```

There was only one `app_user` row and one `user_identity` row, both tied to the Google email address. Therefore the initial missing-books symptom was not a duplicate-user/identity issue.

## PostgreSQL Runtime Mapper Bug

After migration, the UI showed projects but no books.

Logs showed:

```text
GET /api/projects/{id}/books HTTP/1.1 500
SQLException: conversion to class java.lang.Double from numeric not supported
```

Root cause:

`BookDao.map()` used typed JDBC conversion like:

```java
rs.getObject("page_width_in", Double.class)
```

This worked with H2 but failed with PostgreSQL because PostgreSQL JDBC returns `NUMERIC`/`DECIMAL` as `BigDecimal` and does not support direct conversion to `Double` through that typed `getObject` call.

Fix pattern:

```java
private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
    Object value = rs.getObject(column);
    if (value == null) {
        return null;
    }
    if (value instanceof Number n) {
        return n.doubleValue();
    }
    return Double.valueOf(value.toString());
}

private double getRequiredDouble(ResultSet rs, String column) throws SQLException {
    Double value = getNullableDouble(rs, column);
    if (value == null) {
        throw new SQLException("Required numeric column is null: " + column);
    }
    return value;
}
```

Then map page-layout fields using:

```java
.pageWidthIn(getNullableDouble(rs, "page_width_in"))
.pageHeightIn(getNullableDouble(rs, "page_height_in"))
.pageMarginTopIn(getRequiredDouble(rs, "page_margin_top_in"))
.pageMarginBottomIn(getRequiredDouble(rs, "page_margin_bottom_in"))
.pageMarginInnerIn(getRequiredDouble(rs, "page_margin_inner_in"))
.pageMarginOuterIn(getRequiredDouble(rs, "page_margin_outer_in"))
```

Watch for the same anti-pattern anywhere else:

```java
rs.getObject("some_numeric_column", Double.class)
```

against PostgreSQL `NUMERIC`/`DECIMAL`.

## Useful Operational Commands

### PostgreSQL

```bash
systemctl --user status postgres.service --no-pager
journalctl --user -u postgres.service -n 100 --no-pager

podman exec novelkms-postgres \
  psql -U novelkms -d novelkmsdb \
  -c 'SELECT current_database(), current_user;'

podman exec novelkms-postgres \
  psql -U novelkms -d novelkmsdb \
  -c '\dt'
```

### NovelKMS

```bash
systemctl --user status novelkms.service --no-pager
journalctl --user -u novelkms.service -n 100 --no-pager
tail -f /home/rsand/novelkms-container/logs/novelkms.log
tail -f /home/rsand/novelkms-container/logs/novelkms-access.log
```

### Caddy

```bash
systemctl --user status caddy.service --no-pager
journalctl --user -u caddy.service -n 100 --no-pager
podman ps -a --filter name=caddy
```

## Current Status at End of Thread

Completed:

- PostgreSQL is running and persistent.
- NovelKMS connects to PostgreSQL.
- Flyway migrations completed successfully.
- Local Eclipse H2 data migrated into PostgreSQL.
- Data counts and relationships indicate the migration succeeded.
- Runtime issue with PostgreSQL numeric-to-Double conversion was identified and fixed/rebuilt.
