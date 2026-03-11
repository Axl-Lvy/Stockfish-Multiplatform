// Increase Mocha timeout for long-running concurrency tests
config.client = config.client || {};
config.client.mocha = config.client.mocha || {};
config.client.mocha.timeout = 120000;

// Increase Karma browser-level timeouts so heavy Stockfish computation
// doesn't trigger "Disconnected, because no message in 30000 ms"
config.browserNoActivityTimeout = 120000;
config.browserDisconnectTimeout = 30000;
config.browserDisconnectTolerance = 3;
