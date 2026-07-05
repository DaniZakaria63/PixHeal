# PixHeal

On-device AI image enhancement for Android. Built with Jetpack Compose,
Material 3, LiteRT, and Qualcomm AI Hub models.

First goal: to able to enhance blur image using ESRGAN.

## How It Looks

```
HomeScreen
├── Header (PixHeal + account icon)
├── Search bar
├── Popular Templates (2×3 grid)
│   ├── Super Resolution    ├── Inpainting        ├── Photo Restore
│   ├── Colorize            ├── Denoise           ├── Face Enhance
└── Previous Projects (2-col grid, from RoomDB)

EditScreen
├── Top bar: back arrow · [Undo] [Redo]
├── Image preview area
└── Button: Enhance with ESRGAN
```

## What's Inside

| Module     | What it does                                      |
|------------|---------------------------------------------------|
| `:app`     | UI layer — Compose screens, navigation, ViewModels|
| `:litert`  | JNI bridge to LiteRT — runs ESRGAN inference      |
| `:local`   | RoomDB for project-listing persistence            |
| `:modelpull`| OkHttp model downloader for AOT-GAN              |

## Stack

- Kotlin 2.1.10, Gradle 9.4.1, AGP 9.2.1
- Compose + Material 3 (BOM 2025.12.01)
- Hilt 2.60 (DI)
- Navigation Compose (bottom nav)
- Room 2.7.0 (local persistence)
- kotlinx.serialization (editing state JSON)
- OkHttp 4.12 (model downloading)
- Coil 2.7 (image loading)
- Plus Jakarta Sans + Inter (Google Fonts)

## Architecture

```
User picks image
      │
      ▼
 HomeScreen ──navigate──▶ EditScreen
      │                       │
      │                 EditViewModel
      │                       │
      │              ┌────────┼────────┐
      │              │        │        │
      │      LitertBridge  State   ModelPull
      │     (ESRGAN inf.)  Mgmt.   (AOT-GAN dl)
      │
 ProjectRepository (RoomDB)
```

### Editing State (JSON-DTO)
Every edit step is captured as `EditStep` in `EditingStateManager`.
Undo pops from history, redo is blocked after AI model operations
(ESRGAN enhance, inpainting). State serializes to JSON via kotlinx.serialization.

### Project-listing (RoomDB)
Project-listing only captures active state of editing process.
Two edited images = two records in `projects` table.
Different from editing-information.

## How to Run

1. Place `esrgan.tflite` in `app/src/main/assets/`
2. AOT-GAN model downloads on-demand (URL TBD from Qualcomm AI Hub)
3. Build: `./gradlew :app:assembleDevDebug`
4. Install the APK

## Model Setup

### ESRGAN (Super Resolution)
Place the ESRGAN `.tflite` file under `app/src/main/assets/` and the app loads
it via `LitertBridge.loadModel(assetManager, "esrgan.tflite", SUPER_RESOLUTION)`.

### AOT-GAN (Object Removal / Inpainting)
The model is too large for git. `:modelpull` downloads it on first use
via OkHttp with progress tracking and cancel support. The download URL
comes from Qualcomm AI Hub:

https://aihub.qualcomm.com/mobile/models/aotgan

## Contributing

This is personal project. But feel free to open issues or fork.

## License

Apache 2.0 © 2026 daniza
