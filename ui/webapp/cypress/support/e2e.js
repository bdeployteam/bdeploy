// ***********************************************************
// This example support/index.js is processed and
// loaded automatically before your test files.
//
// This is a great place to put global configuration and
// behavior that modifies Cypress.
//
// You can change the location of this file or turn off
// automatically serving support files with the
// 'supportFile' configuration option.
//
// You can read more here:
// https://on.cypress.io/configuration
// ***********************************************************

// Import commands.js using ES2015 syntax:
import './commands';

if (Cypress.env('DISABLE_COVERAGE') !== 'yes') {
  // Import cypress code-coverage collector plugin
  require('@cypress/code-coverage/support');
}

Cypress.Screenshot.defaults({ overwrite: true });

Cypress.on('uncaught:exception', (err, runnable) => {
  // returning false here prevents Cypress from failing the test.

  // current ng-terminal uses ResizeObserver, which in *some* cases can cause
  // unhandled exceptions in the browser, which are not even detected/handled
  // by Angular, so the global error handler will not see them. This specific
  // error has not influence on functionality and does not cause any problems
  // in real world use.
  if (err.message.includes('ResizeObserver loop')) {
    console.log('IGNORING ERROR', err);
    return false;
  }
});
