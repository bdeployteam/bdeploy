// ***********************************************************
// This example plugins/index.js can be used to load plugins
//
// You can change the location of this file or turn off loading
// the plugins file with the 'pluginsFile' configuration option.
//
// You can read more here:
// https://on.cypress.io/plugins-guide
// ***********************************************************

// This function is called when a project is opened or re-opened (e.g. due to
// the project's config changing)
const request = require('request');
const fs = require('fs-extra');
const cypressTypeScriptPreprocessor = require('./cy-ts-preprocessor');

module.exports = (on, config) => {
  // require('cypress-terminal-report').installPlugin(on);

  on('task', {
    downloadFileFromUrl(args) {
      const fileName = args.fileName;
      return new Promise((resolve, reject) => {
        request({ url: args.url, encoding: null, headers: {} }, function (err, res, body) {
          if (!res) {
            return reject(new Error('No response: ' + err));
          }
          if (res.statusCode !== 200) {
            return reject(new Error('Bad status code: ' + res.statusCode));
          }

          fs.outputFileSync(fileName, body);
          resolve(body);
        });
      });
    },
  });

  on('file:preprocessor', cypressTypeScriptPreprocessor);

  if (config.env.DISABLE_COVERAGE !== 'yes') {
    // enable code coverage collection
    require('@cypress/code-coverage/task')(on, config);
  }

  // IMPORTANT to return the config object
  // with the any changed environment variables
  return config;
};
