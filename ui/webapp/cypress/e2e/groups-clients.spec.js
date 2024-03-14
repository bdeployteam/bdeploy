//@ts-check

describe('Groups Tests (Clients)', () => {
  var groupName = 'Demo';
  var instanceName = 'TestInstance';

  before(() => {
    cy.authenticatedRequest({
      method: 'DELETE',
      url: `${Cypress.config('baseUrl')}/api/auth/admin?name=client`,
      failOnStatusCode: false,
    });
  });

  after(() => {
    cy.authenticatedRequest({
      method: 'DELETE',
      url: `${Cypress.config('baseUrl')}/api/auth/admin?name=client`,
    });
  });

  beforeEach(() => {
    cy.login();
  });

  it('Creates a group', () => {
    cy.visit('/');
    cy.createGroup(groupName);

    cy.visit('/');
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');

    cy.visit('/');
    cy.createInstance(groupName, instanceName, 'Demo Product', '2.0.0');
  });

  it('Prepares the instance', () => {
    cy.visit('/');
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.inMainNavContent(() => {
      cy.contains('.bd-rect-card', 'The instance is currently empty').within(() => {
        cy.get('button[data-cy^="Apply Instance Template"]').click();
      });
    });

    cy.inMainNavFlyin('app-instance-templates', () => {
      cy.fillFormSelect('Template', 'Default Configuration');

      cy.fillFormSelect('Client Apps', 'Apply to Client Applications');
      cy.get('button[data-cy="Next"]').click();

      cy.fillFormInput('Text Value', 'Test').type('{esc}');
      cy.fillFormInput('Sleep Timeout', '5');

      cy.get('button[data-cy="Confirm"]').click();
    });

    cy.inMainNavContent(() => {
      cy.contains('app-config-node', 'Client Applications').within(() => {
        cy.get('tr:contains("Client Test")').should('have.length', 2);
      });

      cy.pressToolbarButton('Save');
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('.bd-rect-card', 'has no active version')
        .should('exist')
        .within(() => {
          cy.waitForApi(() => {
            cy.get('button[data-cy="Install"]').should('be.enabled').click();
          });

          cy.waitForApi(() => {
            cy.get('button[data-cy="Activate"]').should('be.enabled').click();
          });
        });
    });
  });

  it('Tests client applications page', () => {
    cy.visit('/');
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Client Applications');

    cy.inMainNavContent(() => {
      cy.contains('tr', instanceName).should('exist');
      cy.get('tr:contains("Client Test")').should('have.length', 1).click(); // only one shown due to OS!
    });

    cy.screenshot('Doc_ClientApps');

    // current OS
    cy.inMainNavFlyin('app-client-detail', () => {
      cy.get('button[data-cy="Download Installer"]')
        .should('be.enabled')
        .downloadByLocationAssign('test-installer.bin');
      cy.get('button[data-cy^="Click"]').should('be.enabled').downloadByLinkClick('test-click-start.json');
      cy.readFile(Cypress.config('downloadsFolder') + '/' + 'test-click-start.json')
        .its('groupId')
        .should('eq', groupName);

      cy.get('button[data-cy="Download Launcher Installer"]')
        .should('be.enabled')
        .downloadByLocationAssign('test-launcher-installer.bin');
      // intentionally NOT downloading launcher as it is quite huge and downloading is slow even locally.
    });

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Group By');
    });

    // remove the second (OS) grouping level.
    cy.get('div[name="dataGroupingPanel"]')
      .last()
      .within(() => {
        cy.contains('mat-icon', 'close').click();
      });

    cy.get('.cdk-overlay-backdrop-showing').click('top', { force: true });

    cy.get('tr:contains("Client Test")').should('have.length', 2);
  });

  it('Creates a local user', () => {
    cy.authenticatedRequest({
      method: 'PUT',
      url: `${Cypress.config('baseUrl')}/api/auth/admin/local`,
      body: { name: 'client', password: 'clientclient' },
    });
  });

  it('Tests no group visible', () => {
    cy.visit('/');
    cy.pressMainNavTopButton('User Settings');
    cy.inMainNavFlyin('app-settings', () => {
      cy.get('button[data-cy="Logout"]').click();
    });
    cy.waitUntilContentLoaded();
    cy.fillFormInput('user', 'client');
    cy.fillFormInput('pass', 'clientclient');

    cy.get('button[type="submit"]').click();

    cy.inMainNavContent(() => {
      cy.contains('Welcome to BDeploy').should('exist');
    });
  });

  it('Assigns Client permissions', () => {
    cy.visit('/');
    cy.enterGroup(groupName);

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Group Settings');
    });

    cy.inMainNavFlyin('app-settings', () => {
      cy.get('button[data-cy^="Instance Group Permissions"]').click();
      cy.contains('tr', 'client')
        .should('exist')
        .within(() => {
          cy.get('button[data-cy^="Modify permissions"]').click();
        });

      cy.intercept({ method: 'GET', url: `/api/group/${groupName}/users` }).as('getUsers');

      cy.waitForApi(() => {
        cy.contains('app-bd-notification-card', 'Modify').within(() => {
          cy.fillFormSelect('modPerm', 'CLIENT');
          cy.get('button[data-cy="OK"]').click();
        });
      });

      cy.wait('@getUsers');

      cy.contains('tr', 'client').within(() => {
        cy.contains('.local-CLIENT-chip', 'CLIENT').should('exist');
      });
    });
  });

  it('Tests clients visible', () => {
    cy.visit('/');
    cy.pressMainNavTopButton('User Settings');
    cy.inMainNavFlyin('app-settings', () => {
      cy.get('button[data-cy="Logout"]').click();
    });
    cy.waitUntilContentLoaded();
    cy.fillFormInput('user', 'client');
    cy.fillFormInput('pass', 'clientclient');

    cy.get('button[type="submit"]').click();

    cy.inMainNavContent(() => {
      cy.contains('Welcome to BDeploy').should('not.exist');
    });

    cy.inMainNavContent(() => {
      cy.contains('tr', groupName).should('exist').click();
      cy.contains('mat-toolbar', 'Client Applications').should('exist');
    });
    cy.pressMainNavButton('Client Applications');

    cy.inMainNavContent(() => {
      cy.contains('tr', instanceName).should('exist');
      cy.get('tr:contains("Client Test")').should('have.length', 1); // only one shown due to OS!
    });
  });
});
