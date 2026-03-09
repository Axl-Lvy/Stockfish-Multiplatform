// Increase Mocha timeout for long-running concurrency tests
config.client = config.client || {};
config.client.mocha = config.client.mocha || {};
config.client.mocha.timeout = 120000;
