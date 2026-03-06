// Provide fallbacks for Node.js modules used by stockfish.wasm package
// (these are only needed in Node.js, not in the browser)
config.resolve = config.resolve || {};
config.resolve.fallback = Object.assign(config.resolve.fallback || {}, {
    "fs": false,
    "path": false,
    "perf_hooks": false,
    "worker_threads": false
});
