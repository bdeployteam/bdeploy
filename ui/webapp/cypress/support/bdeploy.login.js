//@ts-check

Cypress.Commands.add('login', function () {
  cy.fixture('login.json').then((user) => {
    cy.request({
      method: 'POST',
      url: Cypress.env('backendBaseUrl') + '/auth',
      body: { user: user.user, password: user.pass },
    });
    cy.getCookie('st').should('exist');
  });
});

Cypress.Commands.add('loginCentral', function () {
  cy.fixture('login.json').then((user) => {
    cy.request({
      method: 'POST',
      url: Cypress.env('backendBaseUrlCentral') + '/auth',
      body: { user: user.user, password: user.pass },
    });
    cy.getCookie('st').should('exist');
  });
});

Cypress.Commands.add('loginManaged', function () {
  cy.fixture('login.json').then((user) => {
    cy.request({
      method: 'POST',
      url: Cypress.env('backendBaseUrlManaged') + '/auth',
      body: { user: user.user, password: user.pass },
    });
    cy.getCookie('st').should('exist');
  });
});

Cypress.Commands.add('visitCentral', function (url) {
  cy.loginCentral();
  return cy.forceVisit(Cypress.env('baseUrlCentral') + url);
});

Cypress.Commands.add('visitManaged', function (url) {
  cy.loginManaged();
  return cy.forceVisit(Cypress.env('baseUrlManaged') + url);
});

// this allows, together with disabled chrome web security to cross-origin visit our different servers
// without reloading all of cypress.
Cypress.Commands.add('forceVisit', (url) => {
  cy.get('body').then((body$) => {
    const appWindow = body$[0].ownerDocument.defaultView;
    const appIframe = appWindow.parent.document.querySelector('iframe');

    // We return a promise here because we don't want to
    // continue from this command until the new page is
    // loaded.
    return new Promise((resolve) => {
      appIframe.onload = () => {
        resolve();
      };

      // append a dummy get parameter so the iframe is forced to reload on hash change.
      appWindow.location.assign(url.replace('/#/', '/?d=' + Date.now() + '#/'));
    });
  });
});

Cypress.Commands.add('visitBDeploy', function (url, mode) {
  if (mode === 'STANDALONE') {
    return cy.forceVisit(Cypress.config('baseUrl') + url);
  } else if (mode === 'CENTRAL') {
    return cy.visitCentral(url);
  } else if (mode === 'MANAGED') {
    return cy.visitManaged(url);
  } else {
    throw new Error('Unsupported mode: ' + mode);
  }
});

Cypress.Commands.add(
  'authenticatedRequest',
  function (opts, mode = 'STANDALONE') {
    if (mode === 'STANDALONE') {
      cy.login();
    } else if (mode === 'MANAGED') {
      cy.loginManaged();
    } else {
      cy.loginCentral();
    }

    return cy.getCookie('st').then((cookie) => {
      opts.headers = { Authorization: 'Bearer ' + cookie.value };
      return cy.request(opts);
    });
  }
);
