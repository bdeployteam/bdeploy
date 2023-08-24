import { defineConfig } from 'cypress';

export default defineConfig({
  projectId: 'k88zyw',
  defaultCommandTimeout: 60000,
  requestTimeout: 30000,
  responseTimeout: 60000,
  // required for central/managed test to forceVisit.
  chromeWebSecurity: false,
  viewportWidth: 1280,
  viewportHeight: 720,
  videoCompression: 0,
  numTestsKeptInMemory: 5,
  e2e: {
    // We've imported your old cypress plugins here.
    // You may want to clean this up later by importing these.
    setupNodeEvents(on, config) {
      return require('./cypress/plugins/index.js')(on, config);
    },
    baseUrl: 'https://localhost:7707',
    specPattern: 'cypress/e2e/**/*.{js,jsx,ts,tsx}',

    // required for origin switching in cypress 12
    // experimentalOriginDependencies: true,
  },
});
