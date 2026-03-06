// Enable SharedArrayBuffer for stockfish.wasm pthreads support
config.customHeaders = config.customHeaders || [];
config.customHeaders.push(
    { match: '.*', name: 'Cross-Origin-Opener-Policy', value: 'same-origin' },
    { match: '.*', name: 'Cross-Origin-Embedder-Policy', value: 'credentialless' }
);
