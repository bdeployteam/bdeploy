//@ts-check
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
const fs = require('fs-extra');
const { rmdir, rename } = require('fs');
const AdmZip = require('adm-zip');

module.exports = (on, config) => {
  on('task', {
    downloadFileFromUrl(args) {
      const fileName = args.fileName;
      return new Promise((resolve, reject) => {
        import('got').then((got) =>
          got
            .got(args.url, { dnsLookupIpVersion: 4 })
            .then((res) => {
              if (!res || res?.statusCode !== 200) {
                return reject(
                  new Error('No or bad response: ' + res?.statusCode)
                );
              }

              fs.outputFileSync(fileName, res.rawBody);
              resolve(res.body); // *body* might make not much sense in case of binary files, only usable with text files.
            })
            .catch((err) => {
              return reject(new Error('Error in request: ' + err));
            })
        );
      });
    },

    deleteFolder(folderName) {
      console.log('deleting folder %s', folderName);

      return new Promise((resolve, reject) => {
        rmdir(folderName, { maxRetries: 10, recursive: true }, (err) => {
          if (err && err.code !== 'ENOENT') {
            console.error(err);

            return reject(err);
          }
          resolve(null);
        });
      });
    },

    moveFile(args) {
      const from = args.from;
      const to = args.to;
      console.log('moving file %s to %s', from, to);

      return new Promise((resolve, reject) => {
        rename(from, to, (err) => {
          if (err) {
            console.error(err);
            return reject(err);
          }
          resolve(null);
        });
      });
    },

    validateZipFile(args) {
      const { filename, expectedEntry } = args;

      if (!filename) {
        throw new Error(`filename not specified: ${filename}`);
      }
      if (!expectedEntry) {
        throw new Error(`Expected entry not specified: ${expectedEntry}`);
      }

      console.log('loading zip', filename);
      const zip = new AdmZip(filename);
      const zipEntries = zip.getEntries();

      const names = zipEntries.map((entry) => entry.entryName).sort();

      console.log('zip file %s has entries %o', filename, names);

      if (!names.includes(expectedEntry)) {
        throw new Error(
          `Expected Entry ${expectedEntry} not found in ${filename}`
        );
      }

      return null;
    },
  });

  if (config.env.DISABLE_COVERAGE !== 'yes') {
    // enable code coverage collection
    require('@cypress/code-coverage/task')(on, config);
  }

  // IMPORTANT to return the config object
  // with the any changed environment variables
  return config;
};
