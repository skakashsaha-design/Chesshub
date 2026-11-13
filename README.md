# ChessHub

An independent, lightweight, offline-first Android chess application built with **Kotlin**, **Jetpack Compose**, and **Room**, specifically engineered to run smoothly on low-end **1 GB RAM** phones running **Android 8.1+** as well as modern Android devices.

> **Disclaimer:** ChessHub is an independent chess application and is not affiliated with or endorsed by Chess.com or Lichess.

---

## Direct APK Download

Download the latest Universal APK (compatible with ARMv7 32-bit, ARM64, and x86/x86_64 Android 7.0+ / Android 8.1+ devices):

- **Direct Latest APK Download (GitHub Releases):**
  `https://github.com/<YOUR_GITHUB_USERNAME>/<YOUR_REPO_NAME>/releases/latest/download/app-debug.apk`
- **All Releases & Checksums:**
  `https://github.com/<YOUR_GITHUB_USERNAME>/<YOUR_REPO_NAME>/releases`
- **CI Build Artifacts (per commit):**
  Open the **Actions** tab in your GitHub repository → select the latest **Build Universal ChessHub APK** workflow run → download **ChessHub-Universal-APK**.

*(Replace `<YOUR_GITHUB_USERNAME>/<YOUR_REPO_NAME>` after pushing this repository to GitHub and creating a `v1.0.0` tag, or run the workflow manually.)*

---

## Key Features

- **Home Hub:** Quick Play, Play vs Bot, Local 2 Player, Online Chess, Puzzles, Continue Unfinished Game, Recent Games, Lichess & Chess.com profile shortcuts, Music shortcut, and Settings.
- **Complete Offline Chess & FIDE Rules:** Legal moves, check, checkmate, stalemate, castling, en passant, pawn promotion, threefold repetition, 50-move rule, insufficient material, draw offers, resign, undo, board rotation, and replay mode.
- **Built-in Offline Bots & Low Memory Mode:**
  - Fictional bots: **Nova** (Beginner, 650), **Rook** (Easy, 950), **Bishop** (Normal, 1250), **Knight** (Advanced, 1550), **Titan** (Expert, 1850), **Master** (2100), and **Custom** difficulty.
  - Supports **Player vs Bot**, **Bot vs Bot**, **Bot Training** (live evaluation bar & hints), and **4-Bot Round Robin Tournaments**.
  - Powered by **`StockfishEngineService`**, a bound Kotlin service hosting **`StockfishUciWrapper`** (detects a pre-bundled `libstockfish.so` in `app/src/main/jniLibs/<abi>/` with `Threads=1`, `Hash=8-16 MB`, `Skill Level` / `UCI_Elo`, and falls back seamlessly to the built-in pure-Kotlin engine).
  - **Low Memory Mode** automatically detects ~1 GB RAM devices, caps search depth/time, uses a compact primitive-array transposition table, and clears/releases search memory after each move.
- **Background-Accurate Chess Clocks:** `1+0`, `2+1`, `3+0`, `3+2`, `5+0`, `5+3`, `10+0`, `10+5`, `15+10`, `30+0`, Unlimited, and Custom controls anchored to `SystemClock.elapsedRealtime()`.
- **Offline Tactical Puzzles:** Curated compact puzzle catalog with move validation, hints, Elo puzzle rating, streak tracking, and history.
- **Local Room/SQLite History, PGN & ECO Openings:** Lazy-loaded game history with search, sort, PGN import/export/share, and lightweight ECO opening recognition.
- **Official Public Profile Integrations:**
  - **Lichess** official public API (`https://lichess.org/api/`) with cached offline fallback and recent public games replay.
  - **Chess.com** official read-only PubAPI (`https://api.chess.com/pub/`) with cached offline fallback and recent games replay.
- **Backend-Ready Online Multiplayer Architecture:** Secure HTTPS/WSS client with server-authoritative move validation protocol, matchmaking queues, private rooms, friend challenges, and reconnection (honestly reports when no backend server URL is configured).
- **Local Music Player:** Foreground `MediaPlayer` + `MediaStyle` notification playback for audio files stored on your device (supports both scoped `MediaStore` scan and zero-permission Storage Access Framework picking) that continues playing during offline chess.
- **Bilingual Support:** Full **English** and **বাংলা (Bangla)** localization.

---

## Architecture

```text
app/src/main/java/com/example/
├── chess/       # Pure Kotlin FIDE rules, Zobrist hashing, FEN/SAN/PGN codecs, ECO openings, Opponent model
├── engine/      # StockfishEngineService, StockfishUciWrapper, low-RAM Kotlin Alpha-Beta fallback & DeviceMemoryAdvisor
├── bots/        # Fictional bots catalog & off-UI-thread round-robin tournament runner
├── offline/     # Timestamp-anchored ChessClockTracker, synthesized sounds, Android 8.1+ safe haptics
├── puzzles/     # Compact curated tactical puzzles
├── lichess/     # Official Lichess public HTTP/NDJSON API client
├── chesscom/    # Official read-only Chess.com PubAPI client
├── online/      # HTTPS/WSS backend-ready multiplayer protocol & client
├── music/       # MediaStore/SAF local audio scanner, MediaPlayback foreground service & controller
├── database/    # Room entities, lazy-loading DAOs, ChessHubDatabase, ChessHubRepository
├── settings/    # Android 8.1+ compatible English/Bangla LocaleHelper
└── ui/          # Theme, pure-vector Canvas ChessBoardView & piece renderer, and all 8 Compose screens
```

---

## Build Locally & Push to GitHub

```bash
# Run unit & Robolectric tests
./gradlew :app:testDebugUnitTest

# Build universal Debug APK
./gradlew :app:assembleDebug

# Push to GitHub and trigger direct APK release
git init
git add .
git commit -m "Initial ChessHub Android release"
git branch -M main
git remote add origin https://github.com/<YOUR_GITHUB_USERNAME>/<YOUR_REPO_NAME>.git
git push -u origin main
git tag v1.0.0
git push origin v1.0.0
```
