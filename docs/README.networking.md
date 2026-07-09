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

## 2. Application packaging

The NovelKMS distribution is a shaded/uber JAR named `novelkms.jar`. The JAR contains:

- Dropwizard backend and Jersey API.
- React/Vite authenticated application under `webapp/`.
- Hugo-generated public static site under `site/`.

Runtime URL ownership:

```
https://novelkms.com/        -> Hugo static site
https://novelkms.com/app/    -> React SPA
https://novelkms.com/api/*   -> Dropwizard/Jersey API
```

The Maven module split is:

```
novelkms-backend   -> backend/API/server
novelkms-frontend  -> React/Vite SPA, packaged as webapp/**
novelkms-static    -> Hugo site, packaged as site/**
novelkms-distro    -> shaded runnable JAR
```

Generated frontend and Hugo output are not committed. Maven builds them and merges them into the final distro JAR.

## 3. Frontend and static-site serving

Dropwizard serves the public Hugo site and the React app as separate static bundles:

```
bootstrap.addBundle(
        new AssetsBundle(
                "/site",
                "/",
                "index.html",
                "site-assets"));

bootstrap.addBundle(
        new AssetsBundle(
                "/webapp",
                "/app/",
                "index.html",
                "app-assets"));
```

The two bundles must use unique names. Using the default `assets` name for both causes a servlet-name collision.

The React app is built with:

```
VITE_APP_BASENAME=/app
```

so production asset paths are emitted as:

```
/app/assets/...
```

The SPA fallback filter is scoped to `/app` routes. It must not swallow `/`, `/faq/`, `/privacy/`, `/terms/`, or other Hugo-owned public paths.

Expected route behavior:

```
/                         Hugo
/faq/                     Hugo
/privacy/                 Hugo
/terms/                   Hugo
/app/                     React
/app/billing/success      React via SPA fallback
/app/billing/cancel       React via SPA fallback
/app/admin                React via SPA fallback
/api/auth/status          API
```

## 4. OAuth checklist update

When deploying the `/app` split, keep OAuth provider callback URLs under `/api`:

```
https://novelkms.com/api/auth/{provider}/callback
```

But configure the frontend landing/base URL as:

```
https://novelkms.com/app
```

If the canonical domain changes, update both the application config and each OAuth provider console.

## 5. Application Container

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

## 6. Configuration and Secrets

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

## 7. PostgreSQL Persistence

NovelKMS production is using PostgreSQL, which runs in its own pod.

```yaml
database:
  url: jdbc:postgresql://novelkms-postgres:5432/novelkmsdb
```



## 8. Dynamic DNS

Cloudflare tunneling is used to route in novelkms.com

## 9. Home Network Topology

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

Port forwarding is no longer needed because we are using Cloudflare tunneling.

### Podman/Caddy

```text
Fedora host port 8443
→ Caddy container port 443
```

Users do not include port `8443` in the URL. They use standard HTTPS:

```text
https://novelkms.com
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
novelkms.com {
    reverse_proxy novelkms:8080
}

www.novelkms.com, novelkms.richardsand.com {
    redir https://novelkms.com{uri} permanent
}
```

Caddy forwards requests to NovelKMS through the Fedora host's published port 8080.

The container can be run manually with:

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

## 12. Current Deployment State

At the current checkpoint:

- Cloudflare provides DDNS and ingres. 
- Cloudflare daemon runs on media server
- Public DNS works
- Fedora accepts inbound 8443
- Caddy serves a trusted certificate
- The React frontend loads publicly
- OAuth works from several providers
- NovelKMS runs in a rootless Podman container
- PostgreSQL persistence is external to the container
- Caddy certificate state is external to the container

The application and Caddy are still being run manually.

## 13. Useful commands

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
