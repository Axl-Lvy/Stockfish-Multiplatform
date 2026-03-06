# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin Multiplatform library (`io.github.axl-lvy:library`) that wraps the Stockfish chess engine for use on Android, iOS, JVM, and WebAssembly (browser) targets. Stockfish binaries are downloaded separately via Gradle tasks and bundled as resources.

## Build Commands

```bash
# Build the library
./gradlew :library:build

# Download all Stockfish binaries (required before building for the first time)
./gradlew :library:downloadStockfishBinaries

# Download binaries for a specific platform
./gradlew :library:downloadStockfishLinux
./gradlew :library:downloadStockfishWindows
./gradlew :library:downloadStockfishMacOS
./gradlew :library:downloadStockfishAndroidArm64
./gradlew :library:downloadStockfishAndroidArm32
./gradlew :library:downloadStockfishIOS
./gradlew :library:extractStockfishWasm

# Run JVM tests
./gradlew :library:jvmTest

# Run Android instrumented tests (requires connected device/emulator)
./gradlew :library:connectedAndroidTest

# Clean (also removes downloaded Stockfish resources)
./gradlew :library:clean

# Publish to Maven Central
./gradlew :library:publishToMavenCentral
```

## Architecture

The library uses Kotlin Multiplatform's `expect`/`actual` pattern:

- **`commonMain/StockfishEngine.kt`** — The `StockfishEngine` interface (start, sendCommand, readLine, readAllLines, close) used by consumers.
- **`commonMain/StockfishEngineFactory.kt`** — `expect fun createStockfishEngine(): StockfishEngine` — entry point for users.
- **Platform `actual` implementations:**
  - `jvmMain/StockfishEngineFactory.jvm.kt` + `JvmStockfishEngine.kt` — extracts the platform binary from JAR resources to a temp directory, spawns a `Process`.
  - `androidMain/StockfishEngineFactory.android.kt` + `AndroidStockfishEngine.kt` — same approach but extracts to `context.cacheDir`; ABI detection (arm64-v8a / armeabi-v7a) selects the right binary.
  - `androidMain/StockfishMultiplatformInitializer.kt` — `androidx.startup` initializer that captures `applicationContext` automatically; alternatively call `initializeStockfishMultiplatform(context)` manually.
  - `iosMain/StockfishEngineFactory.ios.kt` — stub (`TODO`).
  - `wasmJsMain/WasmStockfishEngine.kt` — stub (`TODO`); WASM files (stockfish.wasm, stockfish.js, stockfish.worker.js) are downloaded from npm package `stockfish.wasm@0.10.0`.

## Stockfish Binaries

Binaries are **not committed** to the repo. They live under `library/src/<platform>Main/resources/stockfish/` and are downloaded by the Gradle download tasks. The `clean` task also deletes them.

- JVM: `stockfish-windows-x86-64.exe`, `stockfish-macos-x86-64`, `stockfish-ubuntu-x86-64`
- Android: `stockfish-arm64-v8a`, `stockfish-armeabi-v7a` (renamed from upstream armv8/armv7 tarballs)
- iOS: `stockfish` (Apple Silicon binary)
- WASM: `stockfish.wasm`, `stockfish.js`, `stockfish.worker.js`

All binaries come from the official Stockfish GitHub releases (`sf_17.1`).

## Package / Coordinates

- Group: `io.github.axl-lvy`
- Artifact: `library`
- Version: `0.1.0`
- Package name: `io.github.axl_lvy.stockfish_multiplatform`