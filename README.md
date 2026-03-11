# Stockfish Multiplatform

A Kotlin Multiplatform library that brings the [Stockfish](https://stockfishchess.org/) chess engine to Android, iOS, JVM (Linux/macOS/Windows), and WebAssembly. Compiles Stockfish from source and bundles it as a native library — just add a dependency and start using it.

## Quick start

```kotlin
// Full — includes both large and small NNUE networks
implementation("fr.axl_lvy:stockfish-multiplatform:<version>")

// Lite — small NNUE network only, much smaller download size
implementation("fr.axl_lvy:stockfish-multiplatform-lite:<version>")
```

```kotlin
val engine = getStockfish()
engine.setPosition(fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1")
val result = engine.search(depth = 20)
println(result.bestMove) // e.g. "e7e5"
```

## Full vs Lite

Both modules expose the same API — the only difference is the bundled NNUE network:

| | Full | Lite |
|---|---|---|
| **NNUE networks** | Large + small | Small only |
| **Strength** | Maximum | Slightly weaker |
| **Binary size** | Larger | Smaller |

The full variant re-evaluates positions with the large network when the small network's evaluation is uncertain, giving it a slight accuracy edge. The lite variant skips this and always uses the small network, resulting in a much smaller artifact.

## Important notes

- **Singleton**: The native Stockfish bridge uses global static state — only one engine instance can exist per process. `getStockfish()` enforces this by returning the same instance on subsequent calls, unless the previous one was closed.
- **Thread-safety**: The high-level API (`setPosition`, `search`, `setOption`, `postMessage`) is mutex-serialized and safe to call from any coroutine. `stop()` and `unsafePostMessage()` are intentionally unguarded so they can be called while the mutex is held (e.g. to interrupt a search).
- **`stop()` usage**: Because `search()` holds the mutex until the engine emits `bestmove`, `stop()` must be called from a separate coroutine or thread.
- **`close()` behavior**: After `close()`, `isClosed` returns `true` and the next call to `getStockfish()` creates a fresh engine instance.
- **Platform support**: JVM (Linux/macOS/Windows), Android, WebAssembly. iOS support is stubbed.
