# NovelKMS PostgreSQL Migration and Deployment Automation

## Purpose

This document records the deployment, logging, and operational fixes completed during the same thread.

This thread began after the following infrastructure was already complete:

- NovelKMS and Caddy were running as rootless Podman Quadlet services.
- Public HTTPS access worked through Caddy.
- Google OAuth worked.
- Reboot persistence was verified.
- Dropbox backup scheduling had been planned/configured.
- The server-side H2 database was known to be disposable.
- The authoritative data source was the local Eclipse H2 database containing the user's real NovelKMS projects and manuscripts.
- Switch from H2 to Postgresql, created new Quadlet

## High-Level Outcome

Completed successfully:

- Maven-based deploy automation was established using SFTP plus webhook.
- The deployment script rebuilds the Podman image and restarts NovelKMS.
- Dropwizard application logs and request logs can be written to external host-mounted files.
- Several container/systemd/webhook operational issues were identified and fixed.

## Maven-Based Deployment Automation

The desired workflow became:

```text
mvn install
    -> build/test locally only

mvn deploy
    -> build/test
    -> package distro JAR
    -> SFTP/SCP JAR to media server
    -> trigger webhook
    -> server deploy script rebuilds Podman image and restarts NovelKMS
```

The user chose:

- Maven Wagon plugin for SFTP upload.
- Webhook to trigger the server-side deploy script.

The deploy process successfully uploaded the new JAR and rebuilt the Podman image.

### Deploy script

The script path:

```text
/home/rsand/bin/deploy-novelkms.sh
```

Responsibilities:

- Use `/home/rsand/novelkms-container` as deployment directory.
- Rebuild `localhost/novelkms:local` from the current `novelkms.jar`.
- Restart `novelkms.service`.
- Optionally perform local/public health checks.
- Log to a deploy logfile.

Important deploy logging pattern:

```bash
LOG_DIR="/home/rsand/novelkms-container/logs"
mkdir -p "$LOG_DIR"

LOG_FILE="$LOG_DIR/deploy-$(date '+%Y%m%d-%H%M%S').log"
ln -sfn "$LOG_FILE" "$LOG_DIR/deploy-latest.log"

exec > >(tee -a "$LOG_FILE") 2>&1

trap 'echo "ERROR: deploy failed on line $LINENO with exit code $?"' ERR
```

Inspect latest deploy log:

```bash
tail -200 /home/rsand/novelkms-container/logs/deploy-latest.log
```

## Webhook / User Systemd Bus Fix

Webhook successfully triggered the script and built the image, but failed on:

```bash
systemctl --user restart novelkms.service
```

Error:

```text
Failed to connect to user scope bus via local transport:
$DBUS_SESSION_BUS_ADDRESS and $XDG_RUNTIME_DIR not defined
```

Webhook showed it executed the script with an empty environment:

```text
environment [] using /home/rsand/bin as cwd
```

Fix:

At the top of the deploy script, before `podman` or `systemctl --user`:

```bash
export XDG_RUNTIME_DIR="/run/user/$(id -u)"
export DBUS_SESSION_BUS_ADDRESS="unix:path=${XDG_RUNTIME_DIR}/bus"
```

This allowed `systemctl --user restart novelkms.service` to work from the webhook process.

Important caution:

If webhook runs as `root`, then `podman build` would build into root's Podman image storage, not `rsand`'s rootless Podman storage. The preferred setup is for webhook/deploy to run as `rsand`.

## User Systemd / Sudo Caveat

The following command is wrong for rootless user services:

```bash
sudo systemctl --user daemon-reload
```

It fails because `sudo` changes the current user to `root`, and root does not have the `rsand` user bus environment.

Correct command when logged in as `rsand`:

```bash
systemctl --user daemon-reload
```

Use no `sudo` for:

```bash
systemctl --user status caddy.service
systemctl --user daemon-reload
systemctl --user restart caddy.service
systemctl --user restart novelkms.service
journalctl --user -u caddy.service -n 100 --no-pager
```

Use `sudo` only for system-level operations such as:

```bash
sudo loginctl enable-linger rsand
sudo firewall-cmd ...
sudo systemctl restart webhook.service
```

## Dropwizard Logging

A top-level Dropwizard `logging:` block captures application logs, but not access logs.

Application logging example:

```yaml
logging:
  level: INFO
  loggers:
    com.richardsand.novelkms: INFO

  appenders:
    - type: console

    - type: file
      currentLogFilename: /logs/novelkms.log
      archivedLogFilenamePattern: /logs/novelkms-%d.log.gz
      archivedFileCount: 14
      timeZone: UTC
```

The external host directory:

```text
/home/rsand/novelkms-container/logs
```

is mounted into NovelKMS as:

```text
/logs
```

Quadlet volume:

```ini
Volume=/home/rsand/novelkms-container/logs:/logs:Z
```

Tail app log:

```bash
tail -f /home/rsand/novelkms-container/logs/novelkms.log
```

### Dropwizard request/access logs

HTTP access logs are configured separately under `server.requestLog`.

If there is already a `server:` section, merge this into it:

```yaml
server:
  requestLog:
    appenders:
      - type: console

      - type: file
        currentLogFilename: /logs/novelkms-access.log
        archivedLogFilenamePattern: /logs/novelkms-access-%d.log.gz
        archivedFileCount: 14
        timeZone: UTC
```

Tail access log:

```bash
tail -f /home/rsand/novelkms-container/logs/novelkms-access.log
```

Successful API calls normally appear in the access log, not the application log. Runtime exceptions appear in the application log only if the application explicitly logs them.

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

### Deploy

```bash
tail -200 /home/rsand/novelkms-container/logs/deploy-latest.log
```

### Inspect unit relationships

```bash
systemctl --user cat caddy.service
systemctl --user cat novelkms.service
systemctl --user list-dependencies --reverse caddy.service
```

## Current Status at End of Thread

Completed:

- Maven deploy now uploads the JAR and triggers a webhook.
- Webhook-triggered deploy can rebuild the Podman image and restart NovelKMS after adding the user-systemd bus environment.
- External Dropwizard app logging is configured.
- Request/access logging configuration was identified.
- Caddy stopping behavior was diagnosed as clean systemd SIGTERM, likely dependency-related, not a crash.
