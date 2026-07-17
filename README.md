# Premium Music Player (Android & PC Local Server)

A modern, high-performance, and feature-rich Android Music Player built with **Jetpack Compose**, **Kotlin**, and **Media3 (ExoPlayer & MediaSession)**. 

To overcome YouTube's aggressive bot/scraping protection (Cloudflare Turnstile, signature blocks) on public downloaders, this app connects to a lightweight, local Python resolver server running on your PC (via `yt-dlp`), making music streaming and downloading 100% reliable and keyless.

---

## Key Features

1. **Auto-Next Track Playback:** ExoPlayer manages the queue natively, transitioning smoothly to the next song when the current one ends.
2. **Notification & Lock Screen Controls:** Media3 session support enables active Next, Previous, Play/Pause buttons, and album artwork display directly in the Android system drawer and lock screen.
3. **Local Library Search:** High-speed filter for your local tracks by Title or Artist.
4. **YouTube Music Streaming & Downloads:** Search any YouTube track or artist in the app, stream it directly in the background, or download the MP3 to your device storage.
5. **Dynamic IP Resolution:** The app automatically detects if it is running on the Android Emulator (`10.0.2.2`) or on a physical mobile device (`192.168.0.103`), communicating with the PC resolver server without code modifications.

---

## Installation & Setup

### 1. Install the APK on your Mobile Phone
The final optimized APK is located in the project root:
- [music-player-release.apk](music-player-release.apk) (Size: **2.2 MB**).
Transfer this file to your phone and install it.

---

## Running the YouTube Stream Resolver Server

Because the app uses your PC's Python environment to resolve audio links, you must run the server on your PC while using the YouTube tab:

### 1. Requirements
Ensure Python is installed on your PC, then update/install `yt-dlp`:
```bash
python -m pip install --upgrade yt-dlp
```

### 2. Start the Server
Open a terminal in the project directory and run:
```bash
python downloader_server.py
```
*Keep this terminal open while using the YouTube features in the app.*

---

## Tech Stack
- **Android App:** Kotlin, Jetpack Compose, Material 3, Coroutines, Media3 (ExoPlayer & MediaSession)
- **Local Server:** Python 3 (built-in `http.server`), `yt-dlp`
