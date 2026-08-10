# Source ledger

## Upstream baseline

- Source: <https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE>
- Commit: `5d807617f8058950f7ea81dda405e38fb0cc37ec`
- Tree: `12511b22201c3dbfcfb9fd79ac9732b347c47255`
- License: GNU GPL version 2 (`LICENSE`)
- Imported scope: the root Gradle build, all 13 modules declared in
  `settings.gradle`, and upstream GitHub workflows.

This repository is an unofficial modified fork. It is not affiliated with or
endorsed by NGA or the upstream author.

## Fork changes

- Favorite boards use `fid + stid` as their stable identity and retain a
  global, persistent order that can be changed by direct long-press drag.
- Topic lists expose one direct post action and article views expose one direct
  reply action. The expandable floating menu and its refresh action are
  removed; pull-to-refresh remains available, and holding the selected article
  page number refreshes that page periodically until release.
- Embedded release keystore paths and passwords are removed. Signing material
  must be supplied outside version control.
- The legacy official-client user-agent claim and identity header are removed;
  requests use the neutral fork identifier `nga-just-works`.
- Upstream PSD design files are excluded from this fork; their branding rights
  are not established and they are not needed to build the application.
- The pinned upstream commit declares `minSdk 30`. This fork restores the
  installation floor to `minSdk 29` while keeping `compileSdk 35`,
  `targetSdk 35`, and the existing single `arm64-v8a` APK strategy.

No credentials, cookies, keystores, local Android SDKs, build output, or user
content are part of the imported source.
