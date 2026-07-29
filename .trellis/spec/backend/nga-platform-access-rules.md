# NGA Platform Access Rules

## Scope And Authority

These rules apply to any code that reads from or writes to NGA, acquires an
NGA session, renders an authenticated NGA WebView, or moves user content to an
upload/media host. The fixed compatibility source is the untouched Justwen
checkout at commit `5d807617f8058950f7ea81dda405e38fb0cc37ec`.

That source proves what one client did. It does not establish an official API,
current availability, authorization, stability, or an SLA. Operation fields
belong in [the operation registry](./nga-platform-operation-registry.md);
transport signatures and login handoff belong in
[the network foundation contract](./network-foundation-contract.md).

## Evidence And Delta Labels

| Label | Meaning | Permitted claim |
| --- | --- | --- |
| `original-source-observed` | Production source exists in the pinned checkout and is wired or explicitly dormant. | The fixed client sent, parsed, or stored this shape. |
| `original-test-or-fixture-backed` | A pinned test or fixture independently fixes request/parser behavior. | The local original contract was tested, not that a server accepts it now. |
| `current-fork-delta` | Reproducible difference between the pinned checkout and current root. | Migration navigation only; never a source for original fields. |
| `authorized-live-verified` | Explicitly authorized, low-frequency, redacted current observation. | Only the observed conditions and time; no stability promise. |
| `unknown-or-unsupported` | Evidence is absent, conflicting, dormant, or intentionally unsupported. | Fail closed and record the gap. |

No operation in the 2026-07-26 bootstrap is `authorized-live-verified`, and
the pinned checkout has no meaningful operation-specific tests or fixtures.

### Original Behavior

The registry preserves endpoint names, fields, parsers, side effects, and even
unsafe implementation choices exactly enough to guide migration.

### Migration Rule

New work must cite an operation ID and preserve the original wire shape only
where it is still intentionally supported. A fork delta may explain why code
changed; it may not fill a missing original fact.

When the maintainer asks to restore or match pinned Justwen behavior, reproduce
that region verbatim and change only what they named. Do not extract a
constant, introduce a shared helper, restyle the surrounding code, or re-shape
it toward another client's captured traffic. Diff the result against
`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen/` and state that it
matches. Any cleanup that seems worthwhile is proposed separately, not shipped
inside the restore.

### Do Not Copy

Do not turn `original-source-observed` into “official,” “stable,” “currently
working,” or “authorized.” Do not infer undocumented fields from another
client, the current fork, an anonymous challenge page, or a remembered session.

## Hosts, Redirects, And Browser Boundaries

### Original Behavior

- Retrofit chooses a preference-backed NGA base URL. Some paths instead pin
  `bbs.nga.cn`, `bbs.ngacn.cc`, `nga.178.com`, `ngabbs.com`, or `img8.nga.cn`.
- Several original paths use cleartext HTTP. Avatar staging posts user image
  bytes to external `app.myauth.us`.
- Original Web login follows and loads arbitrary WebView navigation. The
  generic forum WebView uses substring host tests, not an origin allowlist.
- Parser-generated media URLs include `img*.nga.178.com` and `img.ngacn.cc`.

### Migration Rule

- Define an exact HTTPS scheme, host, and port allowlist for each operation.
  Reject userinfo, non-default ports, ambiguous suffixes, IP substitutions,
  and cleartext downgrade.
- Treat every redirect as a new boundary. Revalidate destination before
  following it, and do not forward account Cookies or sensitive headers to a
  host not explicitly allowed for that operation.
- Keep browser login and generic content browsing separate. Login completion
  must come from validated first-party Cookies; rendered text or JavaScript
  dialogs may only trigger a check.
- External media and upload hosts receive no account Cookie unless an explicit
  operation contract requires it. `app.myauth.us` is legacy external staging,
  not an allowed default.

### Do Not Copy

Do not copy cleartext NGA URLs, substring-based host checks, unrestricted
`loadUrl`, third-party Cookie access, or external avatar staging into new code.

## Identity, Cookies, And Account Selection

### Original Behavior

- The original session Cookies are `ngaPassportUid` and `ngaPassportCid`.
  Web login also reads `ngaPassportUrlencodedUname` and double-decodes it as
  GBK.
- `UserManager` reconstructs a Cookie header from the process-global active
  account. The Retrofit interceptor resolves that account when the request is
  intercepted, not when the operation is created.
- An explicit `Cookie` header overrides the provider. `THREAD.PAGE` uses this
  to retry a parser/server failure with the next account.
- The original interceptor sends `X-User-Agent: Nga_Official`; `HttpUtil`
  constructs `Nga_Official/573` for its generic legacy GET. This fork keeps both
  unchanged.

### Migration Rule

- Select and validate an account before constructing an authenticated
  operation. Bind its immutable identity/session snapshot through send,
  response classification, persistence, and UI delivery.
- Parse exact Cookie names. Validate uid/cid bounds and Cookie-safe characters;
  never accept a complete Cookie header as cid.
- Never send account Cookies to media, redirects, browser fallbacks, or upload
  hosts unless the specific operation authorizes the exact destination.
- Keep the pinned Justwen identity headers (`X-User-Agent: Nga_Official`,
  `Nga_Official/573(...)`) and the browser compatibility UA. NGA gates client
  endpoints on them.
- Account creation/update and active selection must be explicit. Removing an
  account must clear all of its persisted and browser session material.

### Do Not Copy

Do not copy global request-time account lookup, next-account read retry,
plaintext secret rendering, substring Cookie parsing, or login completion based
only on dialog text.

## Encoding And Response Wrappers

### Original Behavior

- The shared Retrofit converter decodes every scalar `String` response as GBK
  and logs it. Legacy `HttpURLConnection` paths also read GBK.
- Requests mix UTF-8 URL encoding, GBK URL encoding, and a Retrofit
  interceptor that decodes an already encoded form then rebuilds it when a
  `charset=gbk` marker appears.
- Common wrappers include `window.script_muti_get_var_store=`,
  `/*error fill content`, `/*$js$*/`, HTML `<title>`, and HTML-embedded JS.
  Original parsers also repair non-standard numeric `content`/`subject`
  tokens and one invalid `__P` segment.
- The attachment upload reads `ResponseBody.string()` rather than the shared
  GBK converter.

### Migration Rule

- The operation codec owns field-by-field request encoding and response
  decoding. Encode exactly once; do not pass pre-encoded values through an
  interceptor that decodes and re-encodes the full body.
- Preserve response bytes until the operation chooses charset from an explicit
  contract or safely parsed content type. Unsupported/malformed charset is a
  protocol failure, not an empty-success payload.
- Normalize known wrappers once at the parser boundary, retain bounded
  redacted diagnostics, then parse a typed data/error union.
- Add local fixtures before changing a wrapper repair. Private messages,
  post bodies, upload tokens, and account fields must never appear in fixture
  names, logs, or failure snapshots.

### Do Not Copy

Do not decode every endpoint as GBK by default, silently turn converter I/O
failures into `""`, log raw bodies, or duplicate wrapper regexes in UI callers.

## Error Classification

### Original Behavior

Original paths frequently collapse distinct cases: parse exceptions become
null or an empty list, absent data becomes “please log in,” 4xx and network
errors become display strings, and challenge/CAPTCHA/rate-limit responses have
no shared classification. A report source comment shows a retry delay, but the
client treats it only as `error.0` text.

### Migration Rule

Every operation must distinguish at least:

| Class | Required handling |
| --- | --- |
| Confirmed success | Apply the documented local side effect once. |
| Confirmed business rejection | Preserve server message as bounded display data; do not mutate local success state. |
| Authentication/session rejection | Stop, bind the failure to the selected account, and request deliberate re-authentication. |
| CAPTCHA/challenge/access-control response | Surface and stop; never solve, bypass, or switch identities automatically. |
| Rate limit/retry delay | Surface retry metadata when trustworthy; no automatic write retry. |
| HTTP/protocol/parse failure before send | Safe retry only if the operation's idempotency contract permits it. |
| Timeout/disconnect after write may have reached server | Return `UnknownOutcome`; preserve user input and require reconciliation or explicit user choice. |

Read parsers may retain NGA site messages, but an HTTP 200 alone is not
business success. Empty, malformed, oversized, or HTML challenge payloads must
not reach a success side effect.

### Do Not Copy

Do not map parse failure to empty success, automatically rotate accounts, or
classify arbitrary substrings such as `成功`, `操作成功`, or a page title as a
stable success protocol.

## Mutation, Retry, And Idempotency

### Original Behavior

Most original mutations use body-text matching and have no idempotency key or
unknown-outcome state. Topic attachment upload retries once after a parsed
`error_code=9` by compressing the image. Check-in may run automatically at app
startup. Message send mutates a shared query map. Notification clear ignores
the response except for logging.

### Migration Rule

- Default all state-changing operations to non-idempotent. Disable transport
  retries unless the operation proves that no bytes were sent or the server
  provides a verified idempotency mechanism.
- Snapshot account, target, payload, attachment token, and expected local
  state before send. Block duplicate UI submissions while in flight.
- A result must point to the submitted operation/account. Apply optimistic UI
  only with a defined rollback and reconciliation path.
- Preserve drafts and attachment references on rejection, challenge, rate
  limit, network failure, cancellation, and `UnknownOutcome`.
- Automatic activity is opt-in, locally bounded, and disabled when a session
  is absent. Do not use startup check-in as precedent for new automation.
- Attachment compression retry is a new upload attempt. Discard or reconcile
  tokens from the prior response before continuing.

### Do Not Copy

Do not auto-retry posts, replies, comments, reports, votes, messages, avatar
changes, notification clearing, or preference mutations. Do not share mutable
request maps across concurrent operations.

## Privacy, Logging, And Storage

### Original Behavior

Original code logs complete Retrofit bodies, raw parser failures, encoded
filter contents, upload failures, request URLs, and response messages. User rows
store cid directly, and message parser failures may include private content.

### Migration Rule

- Never log Cookies, uid/cid pairs, passwords, CAPTCHA, private-message
  subject/body/recipient, post drafts, filter lists, report text, upload bytes,
  attachment checks/tokens, raw responses, or URLs containing those values.
- Redact at the data owner before values enter logger APIs or throwable text.
- Persist session secrets through the project-approved protected store; keep
  content caches account-scoped where server visibility depends on account.
- Bound response, HTML, image, and multipart sizes before retaining or parsing.
- Error telemetry uses operation ID, coarse failure class, HTTP status, and
  random local correlation only.

### Do Not Copy

Do not preserve raw-body logging for compatibility. Do not put secrets in data
class `toString()`, crash reports, task artifacts, tests, or screenshots.

## Authorized Validation

Automated tests use local fixtures, fake servers, or mocks. They must not
contact NGA or related upload/media hosts. A live probe requires separate,
explicit authorization, a named account and operation, request limits,
redaction, challenge/rate-limit stop conditions, and a rollback/reconciliation
plan for writes. Anonymous 403, challenge, or site-message observations are
evidence of uncertainty, not permission to work around access control.

## Review Checklist

- Cite a registry operation ID and its pinned source anchors.
- Keep original behavior, migration rule, and fork delta separate.
- Validate exact HTTPS origin and every redirect.
- Bind one account snapshot; never rotate identity implicitly.
- Apply the operation's exact encoding/wrapper parser once.
- Classify challenge, rate limit, rejection, network failure, and unknown
  mutation outcome explicitly.
- Prove retry/idempotency and local rollback behavior.
- Confirm no sensitive request/response/body/Cookie logging.
- Run only offline tests unless a separate live-access plan is authorized.
