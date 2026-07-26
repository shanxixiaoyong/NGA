# Authorized access validation

The app exposes three low-frequency, manually triggered read probes: board,
topic list, and thread page. A probe accepts only HTTPS URLs on the configured
NGA host allowlist, uses the selected local account's isolated cookie jar, and
records only status, selected non-sensitive headers, charset, response kind,
and byte count. Response bodies are never persisted by the probe repository.

## Required external gate

Using an account and content the tester is authorized to access:

1. Create/select a local account.
2. Import an encrypted credential bundle or complete the controlled Web login.
3. Trigger one board, one topic-list, and one thread-page read with at least one
   second between requests.
4. Confirm each result is a classified JSON/HTML payload, not an empty success.
5. Exercise an expired session, a challenge/blocked page, and a rate-limited or
   `Retry-After` response when they occur naturally. Do not manufacture load.
6. Export only a manually redacted fixture containing metadata and synthetic
   body content. Never commit a Cookie, UID/CID pair, private message, or post
   body.

If the site does not permit stable access, stop. Do not add official-client
headers, challenge solvers, rapid retries, or alternate hosts outside the
allowlist. The downstream product gate remains closed until this document can
be completed with authorized, redacted observations.

## Current status

- Offline classifier/codec/cookie/redirect/rate-limit contracts: implemented.
- Keystore and backup-exclusion device tests: implemented, device run required.
- Authorized board/topic/thread session gate: not run; credentials unavailable.
- RSA/password and captcha server contract: cryptographic primitive and UI
  contract implemented; live endpoint/public-key acceptance not validated.
- Controlled Web login: origin-constrained UI implemented; server acceptance
  and session validation remain external.

