# 🎵 CyroSonic — Next-Generation Music Experience

> **Private Source Code & Architecture Repository**  
> Official Domain: [https://cyrosonic.com](https://cyrosonic.com)  
> Public APK Distribution & Over-The-Air (OTA) Delivery Engine

---

## ⚡ Key Architecture & Features

### 1. In-App Over-The-Air (OTA) APK Updates
- Automated version checking against `https://api.cyrosonic.com/api/version` (with fallback to `https://cyrosonic.com/api/version`).
- Background APK downloading with streaming percentage feedback.
- System package installation integration using Android 14 `FileProvider` (`ACTION_VIEW`, `FLAG_GRANT_READ_URI_PERMISSION`) and `REQUEST_INSTALL_PACKAGES`.
- Remote feature flagging & Audio DSP Equalizer parameter streaming.

### 2. YouTube Music 3-Phase Speed Dial Shelf
- Horizontal 3-phase swipeable pager featuring 27 total curated tracks (3 pages × 9 songs in 3-row grids):
  - **Phase 1 (🔥 Most Listened)**: High-affinity tracks based on all-time listening history (strictly excluding recently played).
  - **Phase 2 (⚡ Recommended)**: Fresh algorithmic discovery picks.
  - **Phase 3 (✨ Related to Artist/Track)**: Contextual recommendations seeded by the user's top-played artists.
- **Dynamic Vibe Themes**: Automatically refreshes aesthetic color palettes, neon accents, and song selections on every pull-to-refresh (*Cyber Horizon*, *Cosmic Chill*, *High Voltage*, *Retro Wave*, *Aurora Emerald*, *Obsidian Pulse*).

### 3. Non-Linear Taste Radio & Pre-Cached Instant Playback
- Tapping any speed dial or catalog track launches instantaneous playback via pre-cached buffers.
- Automatically seeds a dynamic **Taste Radio queue** matching the selected song's mood, artist, and genre rather than playing sequentially in list order.

### 4. Universal Shareable Links (`cyrosonic.com`)
- Every song has a shareable link formatted as `https://cyrosonic.com/track/{id}`.
- Configured with Android App Links and Deep Link Intent Filters to automatically launch and play directly inside the CyroSonic app.
- Web preview page serves rich OpenGraph metadata, cover art, track info, and direct APK download CTAs for new users.

---

## 📂 Project Structure

```
├── hunterxmusic/            # Native Android Application (Kotlin, Jetpack Compose, Media3)
│   ├── app/
│   │   ├── src/main/java/   # Clean Architecture (Core, Domain, Presentation, Service)
│   │   │   └── com/example/hunterxmusic/
│   │   │       ├── core/ota/            # OtaUpdateManager & Version Checker
│   │   │       ├── presentation/home/   # 3-Phase Speed Dial & Dynamic Themes
│   │   │       ├── presentation/player/ # Music Player & Taste Radio
│   │   │       └── service/             # Foreground Media3 Playback Service
│   │   └── build.gradle.kts # Android 14 / Target SDK 36 Build Configuration
├── server/                  # CyroSonic Node.js & TypeScript API & Web Portal
│   ├── index.js             # Express API Server (OTA, Web Preview, Deep Linking)
│   └── package.json         # Server dependencies
└── CyroSonic_Brand/         # Official Brand Assets, Logos & Artwork
```

---

## 🚀 Building & Running

### Android App
```bash
cd hunterxmusic
# Assemble signed release APK
./gradlew assembleRelease
```
The compiled release APK is output to `C:/Users/sande/AppData/Local/Temp/hunterxmusic_build/app/outputs/apk/release/app-release.apk` and copied to the root workspace.

### Backend Server & OTA Portal
```bash
cd server
npm install
npm start
```
Default port: `process.env.PORT || 5000`.

---
*Confidential — Private Source Code of CyroSonic.*
