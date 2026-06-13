# Stockfish Multiplatform

[![Maven Central](https://img.shields.io/maven-central/v/fr.axl-lvy/stockfish-multiplatform?label=Maven%20Central)](https://central.sonatype.com/artifact/fr.axl-lvy/stockfish-multiplatform)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)

**The only Kotlin Multiplatform library that bundles the [Stockfish](https://stockfishchess.org/) 18 chess engine as a native library for JVM, Android, iOS, and WebAssembly.** Add one dependency and call the engine from common code: no manual binaries, no UCI subprocess wiring, no per-platform setup.

Stockfish is compiled from source and packaged for every target, so consumers ship a working engine with a single Gradle coordinate.

## Quick start

```kotlin
// Full — includes both large and small NNUE networks
implementation("fr.axl-lvy:stockfish-multiplatform:<version>")

// Lite — small NNUE network only, much smaller download size
implementation("fr.axl-lvy:stockfish-multiplatform-lite:<version>")
```

`getStockfish()` and `search()` are `suspend` functions, so call them from a coroutine:

```kotlin
suspend fun bestMove(): String {
  val engine = getStockfish()
  engine.setPosition(fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1")
  val result = engine.search(depth = 20)
  return result.bestMove // e.g. "e7e5"
}
```

`getStockfish()` returns a shared singleton, so most apps never close it. If you do call `close()`,
the next `getStockfish()` creates a fresh engine.

## Platforms

| Platform    | Targets                             | How the engine runs                              |
|-------------|-------------------------------------|--------------------------------------------------|
| JVM         | Linux, macOS, Windows (x86_64)      | bundled native library via JNI                   |
| Android     | arm64-v8a, armeabi-v7a, x86_64      | bundled native library via JNI                   |
| iOS         | arm64 device, arm64 + x64 simulator | bundled static library via cinterop              |
| WebAssembly | browser (wasmJs)                    | Web Worker; engine fetched from a CDN at startup |

All targets are implemented and exercised in CI on every change.

## Full vs Lite

Both modules expose the same API — the only difference is the bundled NNUE network:

|                   | Full          | Lite            |
|-------------------|---------------|-----------------|
| **NNUE networks** | Large + small | Small only      |
| **Strength**      | Maximum       | Slightly weaker |
| **Binary size**   | Larger        | Smaller         |

The full variant re-evaluates positions with the large network when the small network's evaluation is uncertain, giving it a slight accuracy edge. The lite variant skips this and always uses the small network, resulting in a much smaller artifact.

## WebAssembly setup

Stockfish uses multi-threaded WebAssembly, which requires the browser to have `SharedArrayBuffer` enabled. Browsers only expose `SharedArrayBuffer` when the page is served with specific HTTP headers.

**Kotlin/WASM with webpack** — create `webpack.config.d/headers.js` in your module directory:

```javascript
if (config.devServer) {
  config.devServer.headers = Object.assign(config.devServer.headers || {}, {
    "Cross-Origin-Opener-Policy": "same-origin",
    "Cross-Origin-Embedder-Policy": "credentialless"
  });
}
```

**Production** — configure your web server to add the same headers to all responses:

```
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Embedder-Policy: credentialless
```

> These headers are a browser requirement for any multi-threaded WebAssembly application, not specific to this library.

> **Runtime download**: on WebAssembly the Stockfish engine is fetched at startup from the public CDN `https://unpkg.com/stockfish@18.0.5/...`. The first `getStockfish()` therefore requires network access, and your Content-Security-Policy must allow `unpkg.com`. This applies only to the WebAssembly target; JVM, Android, and iOS bundle the engine natively.

## Important notes

- **Singleton**: The native Stockfish bridge uses global static state — only one engine instance can exist per process. `getStockfish()` enforces this by returning the same instance on subsequent calls, unless the previous one was closed.
- **Thread-safety**: The high-level API (`setPosition`, `search`, `setOption`, `postMessage`) is mutex-serialized and safe to call from any coroutine. `stop()` and `unsafePostMessage()` are intentionally unguarded so they can be called while the mutex is held (e.g. to interrupt a search).
- **`stop()` usage**: Because `search()` holds the mutex until the engine emits `bestmove`, `stop()` must be called from a separate coroutine or thread.
- **`close()` behavior**: After `close()`, `isClosed` returns `true` and the next call to `getStockfish()` creates a fresh engine instance.
- **Platform support**: JVM (Linux/macOS/Windows), Android, iOS, and WebAssembly. All targets are implemented and exercised in CI.
- **Closed engine**: calling `setPosition`, `setOption`, `postMessage`, or `search` after `close()` throws `IllegalStateException`. If `close()` is called while a `search()` is in flight, that `search()` throws `IllegalStateException` instead of returning a result.
