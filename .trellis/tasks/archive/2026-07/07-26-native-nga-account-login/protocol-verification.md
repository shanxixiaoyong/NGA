# Native Login Protocol Verification

Verified on 2026-07-26 with one bounded, unauthenticated fetch of:

`https://bbs.nga.cn/nuke.php?__lib=login&__act=account&login`

- The endpoint returned HTTP 200 over HTTPS and identified the login-v2 page.
- The document declares GBK and delegates the credential form to
  `/nuke/account_copy.html`.
- Its success handler submits `uid` and `cid` once to
  `nuke.php?__lib=login&__act=login_set_cookie_quick&__output=9`.
- The parent page names `bbs.nga.cn` and `ngabbs.com` as cookie-completion
  origins. The product Web fallback retains the previously reviewed third
  compatibility host `bbs.ngacn.cc` in its explicit allowlist.

A second bounded, unauthenticated inspection of the delegated official page,
`https://bbs.nga.cn/nuke/account_copy.html`, independently verified:

- the current RSA public key used by the product, whose DER SHA-256 fingerprint
  is `1d49cb2093d1577917a576910b23dea5c51053f47771696930a5a79acb5fe3cc`;
- account-type wire values `""`, `mail`, `id`, and `phone`;
- the `__lib`, `__act`, `__output`, `name`, `type`, `password`, and `__inchst`
  login form contract; and
- the CAPTCHA resubmission fields `rid`, `captcha`, and `prid`.

This resolves the earlier evidence gap without treating the observation-only
NgaLite snapshot as protocol authority. The implementation pins the verified
public-key fingerprint, uses RSA/PKCS#1 v1.5 through platform crypto, and fails
closed to the controlled Web flow if the response contract changes. No
credential submission or CAPTCHA image request was made.
