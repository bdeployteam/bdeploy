//@ts-check

Cypress.Commands.add('login', function () {
  return cy.fixture('login.json').then((user) => {
    return cy
      .request({
        method: 'POST',
        url: Cypress.config('baseUrl') + '/api/auth/session',
        body: { user: user.user, password: user.pass },
      })
      .then((resp) => {
        expect(resp.status).to.eq(200);
        cy.getCookie('st').should('exist');
      });
  });
});

Cypress.Commands.add('loginCentral', function () {
  return cy.fixture('login.json').then((user) => {
    return cy
      .request({
        method: 'POST',
        url: Cypress.env('baseUrlCentral') + '/api/auth/session',
        body: { user: user.user, password: user.pass },
      })
      .then((resp) => {
        expect(resp.status).to.eq(200);
        cy.getCookie('st').should('exist');
      });
  });
});

Cypress.Commands.add('loginManaged', function () {
  return cy.fixture('login.json').then((user) => {
    return cy
      .request({
        method: 'POST',
        url: Cypress.env('baseUrlManaged') + '/api/auth/session',
        body: { user: user.user, password: user.pass },
      })
      .then((resp) => {
        expect(resp.status).to.eq(200);
        cy.getCookie('st').should('exist');
      });
  });
});

Cypress.Commands.add('visitCentral', function (url) {
  return cy.loginCentral().then(() => {
    return cy.forceVisit(Cypress.env('baseUrlCentral') + url);
  });
});

Cypress.Commands.add('visitManaged', function (url) {
  return cy.loginManaged().then(() => {
    return cy.forceVisit(Cypress.env('baseUrlManaged') + url);
  });
});

// this allows, together with disabled chrome web security to cross-origin visit our different servers
// without reloading all of cypress.
Cypress.Commands.add('forceVisit', (url) => {
  console.log(`forceVisit ${url}`);
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
    let lg;
    if (mode === 'STANDALONE') {
      lg = cy.login();
    } else if (mode === 'MANAGED') {
      lg = cy.loginManaged();
    } else {
      lg = cy.loginCentral();
    }
    return lg.then(() => {
      return cy.request(opts);
    });
  }
);
