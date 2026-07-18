# PixHeal Project Guide for Agents

This guide contains high-signal facts and operational constraints specific to this Android project, designed to help agents avoid common pitfalls and ramp up quickly.

## 🛠️ Build & Workflow Commands
*   **Build**: The primary command to build the application is `./gradlew :app:assembleDevDebug`. This uses the `dev` product flavor (appends `.dev` to applicationId) with the `debug` build type.
*   **JDK Requirement**: No system JDK is installed. The Android Studio embedded JDK at `/home/dani/opt/android-studio/jbr` (OpenJDK 21.0.10) must be used:
    ```
    export JAVA_HOME=/home/dani/opt/android-studio/jbr
    ./gradlew :app:assembleDevDebug
    ```
    Alternative JDK available at `~/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2` (Temurin 17).
*   **Gradle Config**: Gradle wrapper targets **9.4.1**. Key flags in `gradle.properties`: `org.gradle.jvmargs=-Xmx2048m`, `org.gradle.configuration-cache=false`. The `--no-daemon` flag is useful for CI/one-shot builds.
*   **Model Download**: Large models like AOT-GAN are downloaded on first use via the `:modelpull` module using OkHttp from `https://aihub.qualcomm.com/mobile/models/aotgan`.

## 🏗️ Architecture & Boundaries
The application is divided into several key modules:
*   `:app`: The primary UI layer (Jetpack Compose, Material 3, namespace `id.my.daniza.pixheal`). Handles navigation, ViewModels, and product flavors (`dev`/`prod`).
*   `:litert`: The inference bridge (namespace `id.my.daniza.litert`). **CRITICAL**: This module now uses the pure Kotlin wrapper (`com.google.ai.edge.litert:litert:2.1.0`) instead of a native C API/JNI layer, eliminating previous SIGSEGV crashes. All interactions occur through `LitertBridge.kt`.
*   `:local`: Handles persistence using RoomDB for project listings. Editing state uses a JSON-DTO approach (`EditStep` in `EditingStateManager`) with undo/redo; undo pops from history, but redo is blocked after AI model operations.
*   `:modelpull`: Responsible for downloading large TFLite models from the Qualcomm AI Hub.

## 🧠 Model & Inference Quirks
### General Inference
*   All inference is managed through `LitertBridge` Kotlin wrapper functions (`runSuperRes`, `runInpainting`).
*   The system now uses typed Kotlin exceptions (e.g., `IllegalArgumentException`) for runtime errors, replacing previous native C API SIGSEGV crashes.

### ESRGAN (Super Resolution)
*   **Asset Placement**: The `.tflite` model file (`esrgan.tflite`) must be manually placed in `app/src/main/assets/` (not downloaded).
*   **Input Format**: Input to the inference engine should be provided as a raw `ByteBuffer` containing UINT8 (0-255) pixel values, corresponding to RGB channels.
*   **Output Handling**: The output is a FLOAT32 tensor. It must be manually read from the result buffer and converted into standard Kotlin floats for processing.

### AOT-GAN (Inpainting/Object Removal)
*   Uses a separate model download pipeline (`:modelpull`).
*   Input requires both an image bitmap and a corresponding mask bitmap (binary 0 or 255).

## 📂 File Structure Reference
*   **Core Logic**: `app/src/main/java/id/my/daniza/litert/` contains the core Kotlin bridge logic (`LitertBridge.kt`, `ModelConfig.kt`, `InferenceResult.kt`).
*   **Model Files**: Assets must be in `app/src/main/assets/`.
*   **Documentation**: Detailed processing steps and cross-references to Python/C++ implementations are available in `docs/image-processing-algorithm.md`. Historical SIGSEGV fix context is in `docs/esrgan-sigsegv-fix.md`.
