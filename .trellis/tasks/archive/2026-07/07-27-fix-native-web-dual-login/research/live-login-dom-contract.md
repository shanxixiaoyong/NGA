# Live Login DOM Contract

> Historical investigation only. The native-shell/DOM automation design was
> abandoned after device failures. The final implementation restores the full
> pinned Justwen WebView and does not depend on the selectors below.

Observed on 2026-07-27 with bounded, unauthenticated HTTPS GETs to:

- `https://ngabbs.com/nuke.php?__lib=login&__act=account&login`
- `https://ngabbs.com/nuke/account_copy.html?login`

No credential or login request was sent.

## Outer Page

The exact login page dynamically writes a same-origin iframe:

```html
<iframe id="iff" name="iff" src="/nuke/account_copy.html?login" ...></iframe>
```

The credential DOM therefore belongs to `iframe#iff`, not the outer document.
The outer page also owns the Cookie-completion frames and the final legacy
success alert.

## Credential UI

The delegated page builds `_loginUI` dynamically. It does not expose a
credential `<form>` or a credential form action. Its stable field contract is:

- `select#type`, with values `""`, `mail`, `id`, and `phone`;
- `input#name[type=text]`;
- `input#password[type=password]`.

The page-created login control is an `a[href="javascript:void(0)"]` in the same
login container. It owns `_ready`, `_on`, `_off`, and `onclick`; its click
handler calls the page's `_submit(...)`, which encrypts the password through
the page's `_encrypt(...)`. Successful completion calls
`__appDoAction('loginSuccess', ...)`.

A normal WebView user agent takes the page's Web branch (`__client & 4`), for
which the page-created agreement checkbox starts checked. Challenges are owned
by the same delegated document and session.

## Challenge Surface

The observed delegated page's `_checkCodeInput(...)` creates its CAPTCHA UI as
`window.__checkCodeO`, appends it to the same iframe document, and calls
`_swithDisplay(__checkCodeO)`. That helper hides the other body elements and
records the visible page-owned surface as
`window.__switchDisplay.currentInject`. The surface owns the CAPTCHA image,
input, continue action, and refresh action; continuing triggers the original
login control again in the same JavaScript and Cookie session.

This gives the native shell a narrower challenge gate than exposing arbitrary
page content: require an exact allowed outer/frame origin plus one connected,
visible `currentInject` element owned by the delegated document. If that gate
does not hold after submission, remain in native-shell error state. The full
page is available only through the explicit top-bar fallback.

## Implementation Consequences

The native shell must gate the outer URL, the same-origin iframe URL, exact
field IDs/types/options, and one uniquely matching page-owned login control.
It may fill the three values and dispatch ordinary input/change events only
after a user click, then invoke that unique control's existing click handler.

`form[action]`, `requestSubmit()`, an independent POST, or a direct call to the
page's private submit/encryption functions would encode a false contract. Any
missing, duplicate, cross-origin, or changed credential DOM element must fail
closed in the native shell. A verified `currentInject` challenge may expose the
same attached WebView only inside the shell's bounded challenge region.

## Justwen Lifecycle And Multi-Account Boundary

The pinned Justwen `LoginActivity` creates its WebView in the `AndroidView`
factory and loads the fixed `&login` URL. It does not clear `CookieManager`
before loading. `LoginViewModel` reads the current Passport Cookies only after
the legacy success signal or Activity finish, then passes uid/cid/name to
`UserManager.addUser`.

`UserManager` stores multiple uid/cid rows in Room. Normal Retrofit requests do
not read their session from the login WebView: `NgaClientApp` registers
`UserManagerImpl.getCookie()` as the request Cookie provider, and that adapter
delegates to the active Room-backed user. Therefore a later Web login may
replace the browser's current Cookie without deleting the earlier saved account.
The wrapper must not add pre-login Cookie clearing or treat one WebView instance
as a cross-account session container.
