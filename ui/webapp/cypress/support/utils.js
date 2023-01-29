//@ts-check

/**
 * Delete the downloads folder to make sure the test has "clean"
 * slate before starting.
 */
export const deleteDownloadsFolder = () => {
  const downloadsFolder = Cypress.config('downloadsFolder');

  cy.task('deleteFolder', downloadsFolder);
};

export const validateZip = (zip, expectedEntry, fixture = false) => {
  const downloadsFolder = fixture
    ? Cypress.config('fixturesFolder')
    : Cypress.config('downloadsFolder');
  const downloadedFilename = downloadsFolder + '/' + zip;

  // wait for the file to be fully downloaded by reading it (as binary)
  // and checking its length
  cy.readFile(downloadedFilename, 'binary', { timeout: 15000 }).should(
    'have.length.gt',
    300
  );

  // unzipping and validating a zip file requires the direct access to the file system
  // thus it is easier to perform the checks from the plugins file that runs in Node
  // see the plugins file "on('task')" code to see how we can read and validate a Zip file
  cy.task('validateZipFile', { filename: downloadedFilename, expectedEntry });
};
