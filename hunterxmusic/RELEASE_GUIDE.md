# CyroSonic — Release Build Guide

How to turn the project into a **signed release APK** you can install directly on any Android phone (minSdk 24, Android 7.0+) and share. This is the "direct APK" path — no Google Play account required.

> **One-time heads-up:** everything below has been wired up in `app/build.gradle.kts` and `app/proguard-rules.pro` already. The only things *you* need to do are (1) create a keystore, (2) create `keystore.properties`, and (3) run the build. Steps 1–2 are one-time; after that you just rebuild.

---

## 0. Prerequisites

- **Android Studio** (latest stable) with the Android SDK installed.
- **JDK 17** — bundled with recent Android Studio (Gradle is pinned to Java 17 here).
- The project opens at the `hunterxmusic/` folder (that's the Gradle root — where `settings.gradle`, `gradle.properties` and your new `keystore.properties` live).

---

## 1. Create your signing keystore (one time)

A keystore holds the private key that signs your app. **Every future update must be signed with this same key**, so create it once and back it up carefully.

Open a terminal (Android Studio's **Terminal** tab is fine) and run — from the `hunterxmusic/` folder:

```bash
keytool -genkeypair -v -keystore cyrosonic-release.jks -alias cyrosonic -keyalg RSA -keysize 2048 -validity 10000
```

It will prompt for:

- A **keystore password** and a **key password** (you can use the same value for both — that's what the config assumes if you leave them equal; they *may* differ).
- Your name / org details — these are informational; press Enter through the ones you don't care about.

This produces `cyrosonic-release.jks` in the `hunterxmusic/` folder. `validity 10000` ≈ 27 years, which is deliberately long (Google recommends a key valid past 2033).

### ⚠️ Back up the keystore and passwords

If you lose `cyrosonic-release.jks` or its passwords, you can **never sign an update to this app again** — users would have to uninstall and reinstall a fresh one. Copy the `.jks` file and its passwords somewhere safe (password manager, encrypted backup). It is intentionally **gitignored** so it will not be committed.

---

## 2. Create `keystore.properties` (one time)

In the `hunterxmusic/` folder there is a template named `keystore.properties.example`. Copy it to `keystore.properties` and fill in your real values:

```properties
storeFile=cyrosonic-release.jks
storePassword=your-keystore-password
keyAlias=cyrosonic
keyPassword=your-key-password
```

Notes:

- `storeFile` can be relative to `hunterxmusic/` (as above) or an absolute path. On Windows, use forward slashes or escaped backslashes (`C:\\Users\\sande\\keys\\cyrosonic-release.jks`).
- `keystore.properties`, `*.jks` and `*.keystore` are all in `.gitignore` — they will **not** be committed. Good.
- If this file is missing, the release build still runs but falls back to the **debug** key (fine for local testing, **not** for distribution). The build log will effectively be debug-signed in that case.

---

## 3. Build the signed APK

### Option A — Command line (fastest)

From the `hunterxmusic/` folder:

```bash
# Windows
gradlew.bat clean assembleRelease

# macOS / Linux
./gradlew clean assembleRelease
```

### Option B — Android Studio GUI

1. **Build ▸ Generate Signed Bundle / APK…**
2. Choose **APK** (not Android App Bundle) → **Next**.
3. Select your `cyrosonic-release.jks`, enter the passwords and alias → **Next**.
4. Pick the **release** build variant → **Finish**.

*(Because signing is already configured in Gradle, you can also just select the `release` variant in the Build Variants panel and run **Build ▸ Build APK(s)** — it will sign automatically from `keystore.properties`.)*

### Where the APK lands

```
hunterxmusic/app/build/outputs/apk/release/app-release.apk
```

That `app-release.apk` is your signed, shrunk, installable app.

---

## 4. Install and share

- **Direct install:** copy `app-release.apk` to a phone and open it. The user must allow "Install unknown apps" for the app doing the opening (Files, Chrome, etc.) the first time.
- **Share:** send the APK via Drive/email/USB. Anyone on Android 7.0+ can install it.
- **Verify signing** (optional sanity check), from the SDK `build-tools` folder:

  ```bash
  apksigner verify --print-certs app-release.apk
  ```

---

## 5. What the release build does differently from debug

- **R8 / code shrinking + obfuscation is ON** (`isMinifyEnabled = true`). Keep rules in `app/proguard-rules.pro` protect the reflection-heavy libraries (Gson models, Retrofit interfaces, Room entities, NewPipeExtractor and its JS engine).
- **Resource shrinking is ON** (`isShrinkResources = true`) — unused drawables/layouts are dropped, shrinking the APK.
- **Verbose logs are stripped** — `Log.d` / `Log.v` calls are removed by R8; `Log.w` / `Log.e` are kept so crash diagnostics still work.
- **`versionCode` is now 16, `versionName` is `10.2.0`.** Bump `versionCode` (by 1) for every new build you distribute.

---

## 6. Test checklist after the first signed build

Because R8 renaming *can* break reflection in ways that only show at runtime, please exercise these once on the signed release APK (not just the debug build):

1. **Search + play** a song from online catalog (JioSaavn / YouTube path).
2. **Lyrics** load and scroll/sync for a song that has synced lyrics.
3. **Notification** shows previous / play / next and the buttons work.
4. **Autoplay** — let a song end and confirm a related song plays.
5. **Downloaded / offline** song plays (encrypted local file path).
6. **Device / internal-storage** songs list and play.
7. **Playback speed** control changes speed and the badge updates.
8. **Opening animation** plays on cold start.

If any online feature silently returns nothing *only in release*, it's almost always a missing keep rule — tell me which feature and I'll widen the rule.

---

## 7. Two things to decide before you ship

**a) The AI server URL is dev-only.** In `AppDependencies.kt` the AI backend is:

```
http://10.0.2.2:5000/
```

`10.0.2.2` is the **emulator's** alias for your development machine's `localhost`. On a **real phone**, that address resolves to nothing, so any AI feature will fail to connect in a distributed APK. Before shipping the AI feature you need to deploy your Node AI server to a public host and replace that line with its **HTTPS** URL (and remove the `10.0.2.2` entry from `res/xml/network_security_config.xml`). If you don't use the AI feature, you can ignore this — the rest of the app is unaffected. *(Everything else already talks to public HTTPS endpoints: JioSaavn, lrclib, and Kugou over its HTTP-only lyrics CDN, which is explicitly allowed in the network security config.)*

**b) The AeroChase update URL still references the old GitHub owner.** In `data/remote/AeroChaseCloudSyncManager.kt` the update check points at:

```
https://github.com/SandeepPatel/AeroChase/releases/latest
```

This was **kept intentionally** — it's the functional AeroChase update endpoint, and changing it would break update checks unless that GitHub repo is actually renamed. Leave it if that repo is still yours; change it only if the repo moves.

---

## Reminder

This environment can't compile, run, or sign Android apps — all of the above must be executed in **Android Studio on your machine**. The configuration is in place; the build itself is yours to run.
