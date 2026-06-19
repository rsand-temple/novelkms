# NovelKMS Network and Deployment Summary

## Purpose

This document summarizes the network configuration and deployment work completed so far for hosting NovelKMS from the Fedora media server and making it securely accessible from the public internet.

The deployment is currently functional through:

- `https://novelkms.richardsand.com`
- Google OAuth authentication
- A valid public TLS certificate
- A rootless Podman container running the NovelKMS application
- A separate rootless Podman container running Caddy as the HTTPS reverse proxy
- H2 persistence stored outside the application container

## 1. Host Environment

The application is hosted on a media server running Fedora 43 with Podman 5.6.2. Containers are run rootlessly under the normal `rsand` user account; no Docker daemon is involved.

The working deployment directory is:

```text
/home/rsand/novelkms-container
```

Its current layout is approximately:

```text
novelkms-container/
├── Containerfile
├── novelkms.jar
├── config.yaml
├── novelkms.env
├── Caddyfile
├── data/
├── caddy-data/
└── caddy-config/
```

## 2. Application Packaging

The NovelKMS distribution is a single shaded/uber JAR named `novelkms.jar`. The JAR contains both the Dropwizard backend and the compiled React frontend under the classpath directory `webapp/`.

Confirmed JAR entries include:

```text
webapp/index.html
webapp/assets/index-*.js
```

The application also requires an external `config.yaml`. The choice between H2 and PostgreSQL is controlled through the database section of that file.

## 3. Frontend Serving

Initially, Dropwizard returned `404` for `/` even though the React build was packaged into the JAR.

The fix was to register Dropwizard's `AssetsBundle` in `NovelKmsServer.initialize()`:

```java
bootstrap.addBundle(
    new AssetsBundle(
        "/webapp",
        "/",
        "index.html"
    )
);
```

After rebuilding the uber-JAR, the React frontend became available directly from `http://media:8080/` and later through the public HTTPS endpoint.

## 4. Application Container

The NovelKMS image is built from this Containerfile:

```dockerfile
FROM docker.io/library/eclipse-temurin:17-jre

WORKDIR /opt/novelkms

COPY novelkms.jar /opt/novelkms/novelkms.jar

EXPOSE 8080
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/opt/novelkms/novelkms.jar", "server", "/config/config.yaml"]
```

The image is built with:

```bash
podman build -t novelkms:local .
```

Podman stores the local image as `localhost/novelkms:local`.

The application has been run manually with:

```bash
podman run \
  --name novelkms \
  --rm \
  --env-file "$PWD/novelkms.env" \
  -p 8080:8080 \
  -p 8081:8081 \
  -v "$PWD/config.yaml:/config/config.yaml:ro,Z" \
  -v "$PWD/data:/data:Z" \
  localhost/novelkms:local
```

The `:Z` suffix is required on Fedora so SELinux permits the container to access the mounted files and directories.

## 5. Configuration and Secrets

Secrets were not moved into `config.yaml`. Instead, configuration placeholders continue to use Dropwizard environment substitution, and secret/environment-specific values are stored in `novelkms.env`.

The file is protected with:

```bash
chmod 600 novelkms.env
```

Typical values include OAuth client IDs and client secrets. The environment file must not be committed to Git.

The production-facing auth configuration now uses:

```yaml
auth:
  publicBaseUrl: https://novelkms.richardsand.com
  frontendBaseUrl: https://novelkms.richardsand.com
  secureCookies: true
```

The Google OAuth callback URI is:

```text
https://novelkms.richardsand.com/api/auth/google/callback
```

That exact URI was added to the Google OAuth client's authorized redirect URIs.

## 6. H2 Persistence

For the initial deployment, NovelKMS is still using H2.

The H2 JDBC path inside the container points into `/data`, for example:

```yaml
database:
  url: jdbc:h2:file:/data/novelkms
```

The host directory `/home/rsand/novelkms-container/data` is mounted into the container at `/data`. This allows the H2 database to survive container deletion and image replacement.

The container is disposable; the database files are not.

## 7. Dynamic DNS

DuckDNS is being used as the dynamic DNS provider. A CNAME record was created so that:

```text
novelkms.richardsand.com
```

points to the DuckDNS hostname. This allows users and OAuth providers to use the custom domain while DuckDNS tracks changes to the FiOS public IPv4 address.

The public IP observed during setup was `100.14.78.20`. That address is dynamic and should not be hardcoded as a permanent endpoint.

## 8. Home Network Topology

The home network has two routing layers:

```text
Internet
   |
Verizon FiOS router
   |  Verizon LAN: 192.168.1.0/24
   |
Gateway Eero
   |  Eero WAN address: 192.168.1.157
   |  Eero LAN: 192.168.5.0/24
   |
Media server
      192.168.5.50
```

The media server is physically connected to an Eero mesh device, while the gateway Eero is connected to the Verizon router. This creates double NAT.

The Verizon router cannot forward directly to `192.168.5.50`, because that address exists behind the Eero router.

## 9. Port Forwarding

The working forwarding chain is:

```text
Public TCP 443
    |
Verizon router
    | forwards to 192.168.1.157:8443
    |
Gateway Eero
    | forwards TCP 8443
    |
Media server 192.168.5.50:8443
    |
Caddy container port 443
```

### Verizon FiOS router

```text
External TCP 443
→ Eero WAN IP 192.168.1.157
→ destination port 8443
```

### Eero

```text
External TCP 8443
→ media server 192.168.5.50
→ destination port 8443
```

### Podman/Caddy

```text
Fedora host port 8443
→ Caddy container port 443
```

Users do not include port `8443` in the URL. They use standard HTTPS:

```text
https://novelkms.richardsand.com
```

## 10. Fedora Firewall

TCP port 8443 is open on Fedora:

```bash
sudo firewall-cmd --permanent --add-port=8443/tcp
sudo firewall-cmd --reload
```

The rule was already present when checked.

## 11. Caddy Reverse Proxy

Caddy is running in a separate container and terminates HTTPS.

The Caddyfile is:

```caddyfile
novelkms.richardsand.com {
    reverse_proxy host.containers.internal:8080
}
```

Caddy forwards requests to NovelKMS through the Fedora host's published port 8080.

The container has been run manually with:

```bash
podman run \
  --name caddy \
  --rm \
  -p 8443:443 \
  -v "$PWD/Caddyfile:/etc/caddy/Caddyfile:ro,Z" \
  -v "$PWD/caddy-data:/data:Z" \
  -v "$PWD/caddy-config:/config:Z" \
  docker.io/library/caddy:2
```

Persistent Caddy state is stored in `caddy-data/` and `caddy-config/`. The `caddy-data` directory contains certificate and ACME state and must be backed up.

Caddy successfully obtained and now serves a trusted public certificate for `novelkms.richardsand.com`.

## 12. VPN Routing Problem Encountered

Certificate issuance initially failed even after the double-NAT port forwarding was corrected.

Packet capture showed inbound connections arriving on the physical Ethernet interface `enp4s0`, but replies leaving through `tun0`.

The host-level VPN had installed the default route through the tunnel, producing asymmetric routing:

```text
Inbound:
Internet → FiOS → Eero → enp4s0

Outbound reply:
media server → tun0/VPN
```

Because the reply took a different path, public clients and Let's Encrypt could not complete the TCP handshake.

Disabling the VPN corrected the routing immediately.

Important operational conclusion:

> A full-tunnel VPN must not be active on the Fedora host while NovelKMS is being served publicly, unless policy routing or split tunneling is configured.

The preferred future design is either to leave the host outside the VPN, or place only the applications that require VPN access into their own VPN-aware containers.

## 13. Google OAuth

Once HTTPS was working, Google OAuth still rejected the original callback because it referenced the development/LAN origin.

The deployment was updated to use:

```text
https://novelkms.richardsand.com
```

The exact authorized callback is:

```text
https://novelkms.richardsand.com/api/auth/google/callback
```

After updating both `config.yaml` and the Google Cloud Console OAuth client settings, Google authentication succeeded from a mobile device over the public internet.

## 14. Application Wiring Issue Found

The deployed application later logged repeated HK2 dependency errors for `/api/styles/global`.

`StyleResource` now depends on `UserStyleDao`, but `NovelKmsServer` was still constructing and binding only `StyleDao`.

Since `StyleDao` was no longer referenced by any resource or service, the intended correction is:

- remove the unused `StyleDao` import
- remove its construction
- remove its HK2 binding
- instantiate `UserStyleDao`
- bind `UserStyleDao` using its exact class

Example:

```java
UserStyleDao userStyleDao = new UserStyleDao(ds);
```

and:

```java
bind(userStyleDao).to(UserStyleDao.class);
```

A rebuilt JAR and rebuilt Podman image are required after this source fix.

## 15. Current Deployment State

At the current checkpoint:

- Public DNS works
- DuckDNS tracks the home IP
- The custom domain resolves correctly
- FiOS and Eero forwarding work
- Fedora accepts inbound 8443
- Caddy serves a trusted certificate
- The React frontend loads publicly
- Google OAuth works
- NovelKMS runs in a rootless Podman container
- H2 persistence is external to the container
- Caddy certificate state is external to the container

The application and Caddy are still being run manually.

## 16. Planned Next Steps

1. Finish and verify the `UserStyleDao` production wiring fix.
2. Rebuild and redeploy the NovelKMS image.
3. Confirm that the style endpoints no longer produce HK2 exceptions.
4. Convert the manually run containers into rootless Podman Quadlet/systemd services.
5. Enable user lingering so the services start at boot and survive logout.
6. Reboot-test both services.
7. Define and test backups for:
   - H2 database files
   - `config.yaml`
   - `novelkms.env`
   - Caddy certificate state
   - deployment definitions
8. Replace H2 with PostgreSQL.
9. Add PostgreSQL backup and restore procedures.
10. Consider isolating any VPN-dependent workload so the host's public routing remains stable.

## 17. Operational Commands

### View running containers

```bash
podman ps
```

### View local images

```bash
podman images
```

### Rebuild NovelKMS

```bash
podman build -t novelkms:local .
```

### Confirm listener ports

```bash
sudo ss -lntp | grep -E '8080|8081|8443'
```

### Confirm firewall rule

```bash
sudo firewall-cmd --query-port=8443/tcp
```

### Test public HTTPS

```bash
curl -vk https://novelkms.richardsand.com
```

### Inspect inbound traffic

```bash
sudo tcpdump -ni any tcp port 8443
```

### Check routing

```bash
ip route
ip route get 1.1.1.1
```

## 18. Security Notes

- The application container runs rootless.
- Secrets are kept out of the JAR and out of `config.yaml`.
- `novelkms.env` is protected with mode `600`.
- PostgreSQL is not yet deployed.
- When PostgreSQL is added, it should not publish a host port.
- Caddy is the only public-facing service.
- Dropwizard ports should eventually bind only to localhost or a private Podman network.
- Secure cookies are enabled.
- OAuth uses the public HTTPS origin.
- The host-level VPN must remain disabled unless routing is redesigned.
