// Enable SharedArrayBuffer for stockfish.wasm pthreads support
config.customHeaders = config.customHeaders || [];
config.customHeaders.push(
    { match: '.*', name: 'Cross-Origin-Opener-Policy', value: 'same-origin' },
    { match: '.*', name: 'Cross-Origin-Embedder-Policy', value: 'credentialless' }
);

// Serve stockfish Worker files and proxy the URL
config.files = config.files || [];
config.files.push({
    pattern: 'kotlin/stockfish/**',
    included: false,
    served: true,
    watched: false,
    nocache: false
});
config.proxies = config.proxies || {};
config.proxies['/stockfish/'] = '/base/kotlin/stockfish/';
