# Security

## Reporting a vulnerability

Please use GitHub's **private vulnerability reporting** (Security tab → Report a vulnerability) rather than a public issue.

## Downloads

Release builds are not code-signed yet, so macOS and Windows show a warning on first launch. Instead:
- every release includes `SHA256SUMS`;
- every file has a GitHub **build provenance attestation** (Sigstore), so you can verify it was built by this
  repository's release workflow from the tagged commit:
  ```bash
  gh attestation verify SupermarketManager-1.0.0-macos-arm64.dmg --repo <owner>/supermarket-manager
  ```
Only download from this repository's Releases page.

## Build pipeline

- Workflows run with read-only tokens. Only the release job can write, and only what it needs.
- Every third-party GitHub Action is pinned to a full commit SHA; Dependabot proposes updates.
- Checkout does not keep the token in the workspace (`persist-credentials: false`); frontend installs use `npm ci` from the lockfile.

## The app

- Listens on 127.0.0.1 only and rejects requests whose `Host` is not a loopback name (DNS-rebinding protection).
- Stores data locally in SQLite; it never logs in to retailers, fills carts or handles payments.
