//@ts-check

describe('Admin UI Tests (Settings)', () => {
  beforeEach(() => {
    cy.login();
  });

  it('Tests General Settings', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();

    cy.screenshot('Doc_MainMenu');

    cy.get('button[data-cy=Administration]').click();

    cy.inMainNavContent(() => {
      cy.contains('.mat-mdc-tab', 'General').click();

      cy.contains('app-bd-form-toggle', 'Gravatar').within((toggle) => {
        cy.get('input[type="checkbox"]').should('not.be.checked');
        cy.wrap(toggle).click();
        cy.get('input[type="checkbox"]').should('be.checked');
      });
    });

    cy.screenshot('Doc_Admin_Settings');

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Save');

      cy.contains('app-bd-form-toggle', 'Local User Login').within((toggle) => {
        cy.get('input[type="checkbox"]').should('not.be.checked');
        cy.wrap(toggle).click();
        cy.get('input[type="checkbox"]').should('be.checked');
      });

      cy.pressToolbarButton('Discard');

      cy.contains('app-bd-form-toggle', 'Local User Login').within((toggle) => {
        cy.get('input[type="checkbox"]').should('not.be.checked');
      });

      // revert change for follow up re-runs of the same test.
      cy.contains('app-bd-form-toggle', 'Gravatar').within((toggle) => {
        cy.get('input[type="checkbox"]').should('be.checked');
        cy.wrap(toggle).click();
        cy.get('input[type="checkbox"]').should('not.be.checked');
      });

      cy.pressToolbarButton('Save');
    });
  });

  it('Tests Mail Settings', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.inMainNavContent(() => {
      cy.contains('.mat-mdc-tab', 'Mail Sending').click();

      cy.contains('app-bd-form-toggle', 'Enable Mail Sending').within((toggle) => {
        cy.get('input[type="checkbox"]').should('not.be.checked');
        cy.wrap(toggle).click();
        cy.get('input[type="checkbox"]').should('be.checked');
      });

      cy.fillFormInput('url', 'smtps://smtp.mycompany.com:587');
      cy.fillFormInput('username', 'demo');
      cy.fillFormInput('password', 'demo');

      cy.fillFormInput('receiverAddress', 'central@mycompany.com');
      cy.fillFormInput('managedServerName', 'MyManagedServer');
    });

    cy.screenshot('Doc_Admin_Mail_Sending');

    cy.inMainNavContent(() => {
      cy.contains('.mat-mdc-tab', 'Mail Receiving').click();

      cy.contains('app-bd-form-toggle', 'Enable Mail Receiving').within((toggle) => {
        cy.get('input[type="checkbox"]').should('not.be.checked');
        cy.wrap(toggle).click();
        cy.get('input[type="checkbox"]').should('be.checked');
      });

      cy.fillFormInput('url', 'imaps://imap.mycompany.com:993');
      cy.fillFormInput('username', 'demo');
      cy.fillFormInput('password', 'demo');
    });

    cy.screenshot('Doc_Admin_Mail_Receiving');

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Discard');
    });
  });

  it('Tests Authentication Test', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Test Authentication...');
    });

    cy.inMainNavFlyin('app-auth-test', () => {
      cy.fixture('login.json').then((user) => {
        cy.fillFormInput('user', user.user);
        cy.fillFormInput('pass', user.pass);
      });

      cy.intercept({
        method: 'POST',
        url: '/api/auth/admin/traceAuthentication',
      }).as('authCheck');
      cy.get('button[data-cy="Perform Test"]').should('be.enabled').click();
      cy.wait('@authCheck').then((intercept) => {
        expect(intercept.response.statusCode).to.equal(200);
        // stringify - the response is a JSON array;
        expect(JSON.stringify(intercept.response.body)).to.contain('SUCCESS');
      });

      cy.get('button[data-cy="Close"]').click();
    });
  });

  it('Tests LDAP Settings', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.inMainNavContent(() => {
      cy.contains('.mat-mdc-tab', 'LDAP Auth.').click();
      cy.pressToolbarButton('New Server');
    });
    cy.inMainNavFlyin('app-add-ldap-server', () => {
      cy.fillFormInput('server', 'ldap://ldap.test.server');
      cy.fillFormInput('description', 'Test Server');
      cy.fillFormInput('user', 'user');
      cy.fillFormInput('pass', 'pass');
      cy.fillFormInput('base', 'dc=test,dc=server');
    });

    cy.screenshot('Doc_Admin_Ldap_Server_Config');

    cy.inMainNavFlyin('app-add-ldap-server', () => {
      cy.get('button[data-cy="Save"]').should('be.enabled').click();
    });

    cy.inMainNavContent(() => {
      cy.intercept({
        method: 'POST',
        url: '/api/auth/admin/testLdapServer',
      }).as('ldapCheck');
    });

    cy.screenshot('Doc_Admin_Ldap_Servers');

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Test Server').find('button[data-cy^="Check connection"]').click();
    });
    cy.inMainNavFlyin('app-check-ldap-server', () => {
      cy.wait('@ldapCheck').then((intercept) => {
        expect(intercept.response.statusCode).to.equal(200);
        expect(intercept.response.body).to.contain('connection failed');
      });

      cy.get('button[data-cy="Close"]').click();
    });

    cy.contains('tr', 'Test Server').find('button[data-cy^="Remove"]').click();
    cy.contains('tr', 'Test Server').should('not.exist');
  });

  it('Tests Global Attributes', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.inMainNavContent(() => {
      cy.contains('.mat-mdc-tab', 'Global Attributes').click();
      cy.pressToolbarButton('New Attribute');
    });

    cy.inMainNavFlyin('app-add-global-attribute', () => {
      cy.fillFormInput('name', 'Attr1');
      cy.fillFormInput('description', 'Test Attribute');
      cy.get('button[data-cy="Save"]').should('be.enabled').click();
    });
    cy.contains('tr', 'Attr1').should('exist');
    cy.pressToolbarButton('Save');

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_Admin_Global_Attributes');

    cy.visit('/');
    cy.createGroup('Attr-Test-1');

    cy.visit('/');
    cy.createGroup('Attr-Test-2');

    cy.visit('/');
    cy.enterGroup('Attr-Test-1');

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Group Settings');
    });

    cy.inMainNavFlyin('app-settings', () => {
      cy.get('button[data-cy^="Group Attribute Values"]').click();
    });

    cy.inMainNavFlyin('app-attribute-values', () => {
      cy.get('button[data-cy^="Set Attribute Value"]').click();

      cy.contains('app-bd-notification-card', 'Set Attribute Value').within(() => {
        cy.fillFormSelect('attribute', 'Test Attribute');
        cy.fillFormInput('value', 'Test Value');
      });
    });

    cy.screenshot('Doc_SetGlobalAttributeValue');

    cy.inMainNavFlyin('app-attribute-values', () => {
      cy.contains('app-bd-notification-card', 'Set Attribute Value').within(() => {
        cy.get('button[data-cy="Apply"]').should('be.enabled').click();
      });
    });

    cy.pressMainNavButton('Instance Groups');
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Group By');
    });

    cy.contains('mat-card', 'Grouping Level').within(() => {
      // this is NOT a bd-form-select
      cy.get('mat-select[name="grouping-select"]').should('exist').click();
      // escape all .within scopes to find the global overlay content
      cy.document()
        .its('body')
        .find('.cdk-overlay-container')
        .contains('mat-option', 'Test Attribute')
        .should('exist')
        .click();

      cy.contains('mat-checkbox', 'Test Value').within(() => {
        cy.get('input[type="checkbox"]').should('be.checked');
      });
      cy.contains('mat-checkbox', 'No Group').within(() => {
        cy.get('input[type="checkbox"]').should('be.checked');
      });
    });

    cy.screenshot('Doc_GroupingPanel');

    cy.get('.cdk-overlay-backdrop-showing').click('top');

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Test Value').should('exist');
      cy.contains('tr', 'No Group').should('exist');
    });

    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.inMainNavContent(() => {
      cy.contains('.mat-mdc-tab', 'Global Attributes').click();
      cy.contains('tr', 'Attr1').as('attr');
      cy.get('@attr').find('button[data-cy^="Remove"]').click();
      cy.contains('tr', 'Attr1').should('not.exist');
      cy.pressToolbarButton('Save');
    });

    cy.visit('/');
    cy.deleteGroup('Attr-Test-1');

    cy.visit('/');
    cy.deleteGroup('Attr-Test-2');
  });

  it('Tests Plugins', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.inMainNavContent(() => {
      cy.contains('.mat-mdc-tab', 'Plugins').click();
      cy.pressToolbarButton('Upload Plugin');
    });

    cy.inMainNavFlyin('app-add-plugin', () => {
      cy.fillFileDrop('bdeploy-demo-plugin-1.0.1.jar');
      cy.contains('app-bd-file-upload', 'Success').should('exist');
      cy.pressToolbarButton('Close');
    });

    cy.inMainNavContent(() => {
      cy.intercept({ method: 'GET', url: '/api/plugin-admin/list' }).as('pluginList');
      cy.contains('tr', 'pause').should('exist');

      cy.contains('tr', 'bdeploy-demo-plugin').contains('mat-icon', 'check_box').should('exist'); // loaded
      cy.contains('tr', 'bdeploy-demo-plugin').contains('mat-icon', 'public').should('exist'); // global
      cy.contains('tr', 'bdeploy-demo-plugin').find('button[data-cy^="Unload"]').click();

      cy.wait('@pluginList'); // should be called after unload, required to query the *correct* tr now.
    });

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_Admin_Plugins');

    cy.inMainNavContent(() => {
      // re-fetch the row as it is re-created.
      cy.contains('tr', 'bdeploy-demo-plugin').contains('mat-icon', 'check_box_outline_blank').should('exist'); // not loaded
      cy.contains('tr', 'bdeploy-demo-plugin').find('button[data-cy^="Load"]').click();

      cy.wait('@pluginList'); // should be called after load, required to query the *correct* tr now.

      // and re-fetch again.
      cy.contains('tr', 'bdeploy-demo-plugin').should('exist').as('row');
      cy.contains('tr', 'bdeploy-demo-plugin').contains('mat-icon', 'check_box').should('exist'); // loaded again
      cy.contains('tr', 'bdeploy-demo-plugin').find('button[data-cy^="Delete"]').click();

      cy.contains('app-bd-notification-card', 'Delete').within(() => {
        cy.get('button[data-cy=Yes]').click();
      });

      cy.contains('tr', 'bdeploy-demo-plugin').should('not.exist');
    });
  });
});
