# nga-just-works

This is an unofficial, modified fork of
[Justwen/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE),
based on commit `5d807617f8058950f7ea81dda405e38fb0cc37ec`.

The fork restores the upstream client behavior while retaining direct
long-press sorting for favorite boards and a single contextual post/reply
floating action button. It is not affiliated with or endorsed by NGA or the
upstream author.

Build the debug application with:

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
```

Release signing credentials are intentionally not stored in this repository.
The source is distributed under GNU GPL version 2; see `LICENSE` and
`SOURCE_LEDGER.md`.
