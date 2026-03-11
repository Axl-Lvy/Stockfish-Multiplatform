// Increase Mocha timeout for long-running concurrency tests
config.client = config.client || {};
config.client.mocha = config.client.mocha || {};
config.client.mocha.timeout = 600000;

// Increase Karma browser-level timeouts so the 108 MB WASM binary
// has time to compile on slow CI runners before tests start
config.browserNoActivityTimeout = 600000;
config.browserDisconnectTimeout = 60000;
config.browserDisconnectTolerance = 3;
