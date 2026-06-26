# NovelKMS Persistence and Backup Configuration

## Purpose

This document records the work completed to make the NovelKMS and Caddy rootless Podman containers survive reboots and to define the backup approach for the current H2-based deployment.

The deployment runs on the Fedora media server under the normal `rsand` user account.

Primary deployment directory:

```text
/home/rsand/novelkms-container
```

Rootless Quadlet directory:

```text
/home/rsand/.config/containers/systemd
```

## Current Container Persistence Model

The NovelKMS application container and the Caddy reverse-proxy container are disposable. Persistent state is stored in host directories and bind-mounted into the containers.

### NovelKMS persistent state

```text
/home/rsand/novelkms-container/config.yaml
/home/rsand/novelkms-container/novelkms.env
/home/rsand/novelkms-container/data/
```

The H2 database is configured inside the container with a path such as:

```yaml
database:
  url: jdbc:h2:file:/data/novelkms
```

The host directory:

```text
/home/rsand/novelkms-container/data
```

is mounted into the container as:

```text
/data
```

This allows the H2 database to survive container deletion, image rebuilds, service restarts, and host reboots.

### Caddy persistent state

```text
/home/rsand/novelkms-container/Caddyfile
/home/rsand/novelkms-container/caddy-data/
/home/rsand/novelkms-container/caddy-config/
```

`caddy-data` contains certificate and ACME state and must be preserved and backed up.

## Rootless Quadlet Configuration

The manually launched Podman containers were replaced with rootless Podman Quadlet definitions.

Files:

```text
/home/rsand/.config/containers/systemd/novelkms.container
/home/rsand/.config/containers/systemd/caddy.container
```

### NovelKMS Quadlet

```ini
[Unit]
Description=NovelKMS application container

[Container]
Image=localhost/novelkms:local
ContainerName=novelkms

EnvironmentFile=/home/rsand/novelkms-container/novelkms.env

Volume=/home/rsand/novelkms-container/config.yaml:/config/config.yaml:ro,Z
Volume=/home/rsand/novelkms-container/data:/data:Z

PublishPort=8080:8080
PublishPort=127.0.0.1:8081:8081

[Service]
Restart=always
RestartSec=5
TimeoutStartSec=120
TimeoutStopSec=70

[Install]
WantedBy=default.target
```

Notes:

- Port 8080 is published for the application and Caddy upstream access.
- Port 8081, normally the Dropwizard administrative port, is restricted to localhost.
- The environment file remains outside the container image.
- SELinux relabeling uses `:Z` on Fedora.

### Caddy Quadlet

```ini
[Unit]
Description=Caddy reverse proxy for NovelKMS
Requires=novelkms.service
After=novelkms.service

[Container]
Image=docker.io/library/caddy:2
ContainerName=caddy

Volume=/home/rsand/novelkms-container/Caddyfile:/etc/caddy/Caddyfile:ro,Z
Volume=/home/rsand/novelkms-container/caddy-data:/data:Z
Volume=/home/rsand/novelkms-container/caddy-config:/config:Z

PublishPort=8443:443

[Service]
Restart=always
RestartSec=5
TimeoutStartSec=120
TimeoutStopSec=70

[Install]
WantedBy=default.target
```

The Caddy service has an explicit dependency on NovelKMS:

```text
novelkms.service starts before caddy.service
```

The Caddyfile remains:

```caddyfile
novelkms.richardsand.com {
    reverse_proxy host.containers.internal:8080
}
```

## User Lingering

User lingering was enabled for `rsand`:

```bash
sudo loginctl enable-linger rsand
```

Verification:

```bash
loginctl show-user rsand -p Linger
```

Expected result:

```text
Linger=yes
```

This allows the `rsand` user systemd manager to start at boot and remain active without requiring an interactive login. The rootless Quadlet services therefore start automatically after reboot.

## Service Management

Reload generated user services after changing Quadlet files:

```bash
systemctl --user daemon-reload
```

Start the stack:

```bash
systemctl --user start caddy.service
```

Because Caddy requires NovelKMS, starting Caddy also starts NovelKMS.

Check service status:

```bash
systemctl --user status novelkms.service --no-pager
systemctl --user status caddy.service --no-pager
```

Check containers:

```bash
podman ps
```

Check logs:

```bash
journalctl --user -u novelkms.service -n 100 --no-pager
journalctl --user -u caddy.service -n 100 --no-pager
```

Follow both logs:

```bash
journalctl --user \
  -u novelkms.service \
  -u caddy.service \
  -f
```

## Reboot Persistence Verification

The following checks were used after reboot:

```bash
loginctl show-user rsand -p Linger
systemctl --user is-active novelkms.service
systemctl --user is-active caddy.service
podman ps
curl -I http://localhost:8080/
curl -I https://novelkms.richardsand.com/
```

Expected service states:

```text
active
active
```

The application, public HTTPS endpoint, H2 database, and Google OAuth flow remained functional after reboot.

## Backup Strategy

Persistence protects against container replacement and reboot, but it does not protect against disk failure, accidental deletion, database corruption, theft, or host loss.

The selected off-server backup destination is the user's Dropbox account.

Recommended Dropbox destination:

```text
/home/rsand/Dropbox/Backups/NovelKMS
```

Only completed backup archives should be placed in Dropbox. The live H2 database directory should not be directly synchronized because Dropbox could copy the files during an active database write or create conflicted versions.

## Files Included in Backups

Application state and configuration:

```text
/home/rsand/novelkms-container/data/
/home/rsand/novelkms-container/config.yaml
/home/rsand/novelkms-container/novelkms.env
```

Caddy configuration and state:

```text
/home/rsand/novelkms-container/Caddyfile
/home/rsand/novelkms-container/caddy-data/
/home/rsand/novelkms-container/caddy-config/
```

Deployment definitions:

```text
/home/rsand/novelkms-container/Containerfile
/home/rsand/.config/containers/systemd/novelkms.container
/home/rsand/.config/containers/systemd/caddy.container
```

The local NovelKMS image and JAR can be rebuilt and therefore are not the primary backup target, although including the current JAR in a release archive may be useful later.

## H2 Consistency Requirement

NovelKMS should be stopped briefly while the H2 files are copied into the archive.

The backup sequence is:

1. Stop `novelkms.service`.
2. Create the archive from the persistent host files.
3. Verify the archive can be listed.
4. Atomically rename the completed archive into its final Dropbox filename.
5. Restart `novelkms.service`.
6. Remove archives older than the retention period.

Caddy may remain running during the backup. It will temporarily report an unavailable upstream while NovelKMS is stopped.

## Backup Script

Recommended path:

```text
/home/rsand/bin/backup-novelkms.sh
```

Recommended implementation:

```bash
#!/usr/bin/env bash

set -euo pipefail

DEPLOY_DIR="/home/rsand/novelkms-container"
QUADLET_DIR="/home/rsand/.config/containers/systemd"
BACKUP_ROOT="/home/rsand/Dropbox/Backups/NovelKMS"

TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"
BACKUP_FILE="${BACKUP_ROOT}/novelkms-${TIMESTAMP}.tar.gz"
TEMP_FILE="${BACKUP_FILE}.partial"

mkdir -p "$BACKUP_ROOT"

NOVELKMS_WAS_ACTIVE=false
if systemctl --user is-active --quiet novelkms.service; then
    NOVELKMS_WAS_ACTIVE=true
fi

restart_novelkms() {
    if [[ "$NOVELKMS_WAS_ACTIVE" == true ]] && \
       ! systemctl --user is-active --quiet novelkms.service; then
        echo "Restarting NovelKMS..."
        systemctl --user start novelkms.service
    fi
}

cleanup_partial() {
    rm -f "$TEMP_FILE"
}

trap 'cleanup_partial; restart_novelkms' EXIT

if [[ "$NOVELKMS_WAS_ACTIVE" == true ]]; then
    echo "Stopping NovelKMS for a consistent H2 snapshot..."
    systemctl --user stop novelkms.service
fi

echo "Creating backup..."
tar \
    --create \
    --gzip \
    --file "$TEMP_FILE" \
    --absolute-names \
    "$DEPLOY_DIR/data" \
    "$DEPLOY_DIR/config.yaml" \
    "$DEPLOY_DIR/novelkms.env" \
    "$DEPLOY_DIR/Caddyfile" \
    "$DEPLOY_DIR/Containerfile" \
    "$DEPLOY_DIR/caddy-data" \
    "$DEPLOY_DIR/caddy-config" \
    "$QUADLET_DIR/novelkms.container" \
    "$QUADLET_DIR/caddy.container"

chmod 600 "$TEMP_FILE"

echo "Verifying archive..."
tar --list --gzip --file "$TEMP_FILE" >/dev/null

# Dropbox sees the final filename only after the archive is complete.
mv "$TEMP_FILE" "$BACKUP_FILE"

if [[ "$NOVELKMS_WAS_ACTIVE" == true ]]; then
    echo "Starting NovelKMS..."
    systemctl --user start novelkms.service
fi

# Keep approximately 30 days of backup archives.
find "$BACKUP_ROOT" \
    -type f \
    -name 'novelkms-*.tar.gz' \
    -mtime +30 \
    -delete

echo "Backup created:"
echo "$BACKUP_FILE"
```

Permissions:

```bash
chmod 700 /home/rsand/bin/backup-novelkms.sh
chmod 700 /home/rsand/Dropbox/Backups/NovelKMS
chmod 600 /home/rsand/novelkms-container/novelkms.env
```

### Why the `.partial` suffix is used

Dropbox may begin syncing a file as soon as it appears. The archive is therefore created with a `.partial` suffix and renamed only after `tar` successfully verifies it.

The final file appears atomically as:

```text
novelkms-YYYYMMDD-HHMMSS.tar.gz
```

## Backup Scheduling Decision

Cron was considered, but a systemd user timer was selected because the backup script directly controls rootless user services through:

```bash
systemctl --user stop novelkms.service
systemctl --user start novelkms.service
```

A systemd user timer runs in the same `rsand` user service-manager context as the Quadlet services and avoids cron's limited environment and potential user-bus issues.

Additional advantages:

- `Persistent=true` catches up after a missed run caused by downtime.
- Logs are available in the user journal.
- Status and next-run time are easily inspected.
- The backup uses the same service-management system as the containers.
- Failures are visible through normal systemd tools.

Cron would still be technically viable, but the systemd timer is the cleaner and less brittle fit for this deployment.

## Backup Service

File:

```text
/home/rsand/.config/systemd/user/novelkms-backup.service
```

Contents:

```ini
[Unit]
Description=Back up NovelKMS data and deployment configuration
After=novelkms.service

[Service]
Type=oneshot
ExecStart=/home/rsand/bin/backup-novelkms.sh
```

## Backup Timer

File:

```text
/home/rsand/.config/systemd/user/novelkms-backup.timer
```

Contents:

```ini
[Unit]
Description=Nightly NovelKMS backup

[Timer]
OnCalendar=*-*-* 03:00:00
Persistent=true
RandomizedDelaySec=10m
Unit=novelkms-backup.service

[Install]
WantedBy=timers.target
```

Activate it:

```bash
systemctl --user daemon-reload
systemctl --user enable --now novelkms-backup.timer
```

Check timer status:

```bash
systemctl --user status novelkms-backup.timer --no-pager
systemctl --user list-timers --all | grep novelkms
```

Run an immediate test:

```bash
systemctl --user start novelkms-backup.service
journalctl --user -u novelkms-backup.service -n 100 --no-pager
```

Check generated archives:

```bash
ls -lh /home/rsand/Dropbox/Backups/NovelKMS
```

Verify the newest archive:

```bash
LATEST="$(ls -1t /home/rsand/Dropbox/Backups/NovelKMS/*.tar.gz | head -1)"
tar -tzf "$LATEST" | less
```

## Security Notes

The backup archive includes:

```text
novelkms.env
```

This file contains OAuth and other deployment secrets. Therefore:

- Backup archives must use mode `600`.
- Dropbox must have strong MFA enabled.
- The Dropbox account itself becomes part of the deployment security boundary.
- Backup archives should not be shared through public Dropbox links.

A future improvement is to encrypt each completed archive with `age` or a similar tool before placing it in Dropbox.

## Restore Testing

A backup is not considered proven until it has been restored successfully.

Recommended restore test:

1. Copy one completed archive from Dropbox to a temporary directory.
2. Extract it without overwriting the live deployment.
3. Inspect the restored H2 database files and configuration.
4. Launch a temporary NovelKMS container against the restored data on alternate host ports.
5. Confirm that projects, books, chapters, scenes, images, styles, and authentication configuration are present.
6. Stop and remove the temporary validation container.

Do not perform the first restore test directly over the production directories.

## Current Status

Completed:

- NovelKMS runs as a rootless Quadlet service.
- Caddy runs as a rootless Quadlet service.
- Caddy starts after and depends on NovelKMS.
- Rootless services start automatically after reboot.
- User lingering is enabled for `rsand`.
- H2 state remains outside the application container.
- Caddy certificate and ACME state remain outside the Caddy container.
- Reboot persistence has been verified.
- Dropbox was selected as the off-server backup destination.
- Completed archives, rather than live H2 files, will be synchronized.
- A systemd user timer was selected instead of cron.

Next actions:

1. Install the backup script.
2. Create the Dropbox backup directory.
3. Run and verify a manual backup.
4. Install and enable the systemd user timer.
5. Confirm that the archive appears and finishes syncing in Dropbox.
6. Perform a non-destructive restore test.
7. Later, consider encrypted backup archives.
8. After backups and restore are proven, proceed with PostgreSQL migration planning.

## Operational Caveat

The host-level full-tunnel VPN must remain disabled unless split tunneling or policy routing is implemented. A default route through the VPN previously caused asymmetric routing for inbound HTTPS traffic.
