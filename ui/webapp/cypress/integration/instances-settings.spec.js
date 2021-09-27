//@ts-check

describe('Instance Settings Tests', () => {
  var groupName = 'Demo';
  var instanceName = 'TestInstance';

  before(() => {
    cy.cleanAllGroups();
  });

  beforeEach(() => {
    cy.login();
  });

  it('Prepares the test (group, products, instance)', () => {
    cy.visit('/');
    cy.createGroup(groupName);
    cy.uploadProductIntoGroup(groupName, 'test-product-1-direct.zip');
    cy.createInstance(groupName, instanceName, 'Demo Product', '1.0.0');
  });

  it('Tests Base Configuration', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.get('app-configuration').should('exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should('exist');

      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy="Base Configuration"]').click();
    });

    cy.inMainNavFlyin('app-edit-config', () => {
      cy.fillFormInput('name', `${instanceName} (*)`);
      cy.fillFormInput('description', `${instanceName} (*)`);
      cy.fillFormSelect('purpose', 'DEVELOPMENT');
      cy.get('button[data-cy="APPLY"]').click();
    });

    cy.inMainNavContent(() => {
      cy.contains('mat-toolbar', `Configuration - ${instanceName} (*)`).should('exist');
      cy.pressToolbarButton('Local Changes');
    });

    cy.inMainNavFlyin('app-local-changes', () => {
      cy.get('button[data-cy^="Discard"]').click();
      cy.contains('app-bd-notification-card', 'Discard').within(() => {
        cy.get('button[data-cy="YES"]').click();
      });

      cy.get('button[data-cy="Close"]').click();
    });

    cy.checkMainNavFlyinClosed();

    cy.inMainNavContent(() => {
      cy.contains('mat-toolbar', `Configuration - ${instanceName} (*)`).should('not.exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should('exist');
    });
  });

  it('Tests Configuration Files', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.get('app-configuration').should('exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should('exist');

      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Configuration Files"]').click();
    });

    cy.inMainNavFlyin('app-config-files', () => {
      cy.waitUntilContentLoaded();

      cy.contains('tr', 'Current instance configuration files').should('exist');
      cy.contains('tr', 'current product version').should('not.exist');

      cy.contains('tr', 'dummy2.cfg').within(() => {
        cy.get('button[data-cy="Delete"]').click();
      });

      cy.contains('app-bd-notification-card', 'Delete dummy2.cfg').within(() => {
        cy.get('button[data-cy="YES"]').click();
      });

      cy.waitUntilContentLoaded();

      cy.contains('tr', 'dummy2.cfg').should('not.exist');

      cy.contains('tr', 'binary.cfg').within(() => {
        cy.get('button[data-cy="Edit"]').should('be.disabled');
        cy.get('button[data-cy="Rename"]').click();
      });

      cy.contains('app-bd-notification-card', 'Rename binary.cfg').within(() => {
        cy.fillFormInput('newName', 'binary2.cfg');
        cy.get('button[data-cy="CONFIRM"]').should('be.enabled').click();
      });

      cy.pressToolbarButton('Back to Overview');
    });

    // potential validation still running.
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.get('button[data-cy^="Save"]').should('be.enabled').click();
      cy.waitUntilContentLoaded();
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Configuration Files"]').click();
    });

    cy.inMainNavFlyin('app-config-files', () => {
      cy.waitUntilContentLoaded();

      cy.contains('tr', 'current product version').should('exist');

      cy.contains('tr', 'binary2.cfg')
        .should('exist')
        .within(() => {
          cy.contains('File not in selected product version').should('exist');
        });

      cy.waitForApi(() => {
        cy.contains('tr', 'binary.cfg')
          .should('exist')
          .within(() => {
            cy.get('button[data-cy="Delete"]').should('be.disabled');
            cy.get('button[data-cy="Rename"]').should('be.disabled');
            cy.get('button[data-cy="Edit"]').should('be.disabled');

            cy.get('button[data-cy^="Create from product"]').should('exist').and('be.enabled').click();
          });
      });

      cy.contains('tr', 'dummy1.cfg').within(() => {
        cy.get('button[data-cy="Edit"]').click();
      });
    });

    cy.inMainNavFlyin('app-editor', () => {
      cy.monacoEditor().should('contain.value', 'dummy configuration');
      cy.typeInMonacoEditor('Configuration File Content', true);
      cy.pressToolbarButton('APPLY');
    });

    cy.inMainNavFlyin('app-config-files', () => {
      cy.pressToolbarButton('Add File');
      cy.contains('app-bd-notification-card', 'Add Configuration File').within(() => {
        cy.fillFormInput('path', 'test.json');
        cy.get('button[data-cy="OK"]').click();
      });

      cy.contains('tr', 'test.json')
        .should('exist')
        .within(() => {
          cy.get('button[data-cy="Edit"]').click();
        });
    });

    cy.inMainNavFlyin('app-editor', () => {
      cy.monacoEditor().should('exist');
      cy.typeInMonacoEditor('{{}{enter}"json" : "content"');
      cy.pressToolbarButton('APPLY');
    });

    cy.inMainNavFlyin('app-config-files', () => {
      cy.pressToolbarButton('Back to Overview');
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.get('button[data-cy^="Save"]').should('be.enabled').click();
      cy.waitUntilContentLoaded();
    });
  });

  it('Tests Banner', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.get('app-configuration').should('exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should('exist');

      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Banner"]').click();
    });

    cy.inMainNavFlyin('app-banner', () => {
      cy.get('textarea').clear().type('This is a banner text');
      cy.get('input[id="fgColor"]').invoke('val', '#FFFFFF').trigger('input');
      cy.get('input[id="bgColor"]').invoke('val', '#FF0000').trigger('input');

      cy.get('app-bd-banner').within(() => {
        cy.get('.local-banner').should('have.css', 'background-color', 'rgb(255, 0, 0)');
        cy.get('.local-banner').should('have.css', 'color', 'rgb(255, 255, 255)');
        cy.contains('This is a banner text').should('exist');
      });

      cy.get('button[data-cy="APPLY"]').should('be.enabled').click();
    });

    cy.inMainNavContent(() => {
      cy.get('app-bd-banner').within(() => {
        cy.get('.local-banner').should('have.css', 'background-color', 'rgb(255, 0, 0)');
        cy.get('.local-banner').should('have.css', 'color', 'rgb(255, 255, 255)');
        cy.contains('This is a banner text').should('exist');
      });
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Banner"]').click();
    });

    cy.inMainNavFlyin('app-banner', () => {
      cy.get('button[data-cy^="Remove"]').should('be.enabled').click();
      cy.get('button[data-cy^="Remove"]').should('be.disabled');

      cy.get('app-bd-banner').within(() => {
        cy.get('.local-banner').should('not.have.css', 'background-color', 'rgb(255, 0, 0)');
        cy.get('.local-banner').should('not.have.css', 'color', 'rgb(255, 255, 255)');
        cy.contains('This is a banner text').should('not.exist');
      });
    });

    cy.inMainNavContent(() => {
      cy.get('app-bd-banner').should('not.exist');
    });
  });

  it('Tests Ports', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.get('app-configuration').should('exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should('exist');

      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Manage Network Ports"]').click();
    });

    cy.inMainNavFlyin('app-ports', () => {
      // TODO: there is not port test data available to assert on - we're just shifting
      // to go through the code path once.
      cy.get('button[data-cy^="Shift"]').should('be.enabled').click();
      cy.contains('app-bd-notification-card', 'Shift ports').within(() => {
        cy.fillFormInput('amount', '10');
        cy.get('button[data-cy="CONFIRM"]').click();
      });

      cy.get('button[data-cy^="Export"]').downloadByLinkClick('ports.csv');
      cy.readFile(Cypress.config('downloadsFolder') + '/ports.csv').then((content) => {
        expect(content).to.contain('Application,Name,Description,Port');
      });
    });
  });

  it('Tests Nodes', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.get('app-configuration').should('exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should('exist');

      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Manage Nodes"]').click();
    });

    cy.inMainNavContent(() => {
      cy.contains('app-config-node', 'master')
        .should('exist')
        .within(() => {
          cy.contains('No data').should('exist');
        });
    });

    cy.inMainNavFlyin('app-nodes', () => {
      cy.get('button[data-cy="APPLY"]').should('be.disabled');
      cy.contains('tr', 'master')
        .should('exist')
        .within(() => {
          cy.get('input[type="checkbox"]').uncheck({ force: true });
        });
      cy.get('button[data-cy="APPLY"]').should('be.enabled').click();
    });

    cy.inMainNavContent(() => {
      cy.contains('app-config-node', 'master').should('not.exist');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Manage Nodes"]').click();
    });

    cy.inMainNavFlyin('app-nodes', () => {
      cy.get('button[data-cy="APPLY"]').should('be.disabled');
      cy.contains('tr', 'master')
        .should('exist')
        .within(() => {
          cy.get('input[type="checkbox"]').check({ force: true });
        });
      cy.get('button[data-cy="APPLY"]').should('be.enabled').click();
    });

    cy.inMainNavContent(() => {
      cy.contains('app-config-node', 'master')
        .should('exist')
        .within(() => {
          cy.contains('No data').should('exist');
        });

      cy.waitUntilContentLoaded();
    });
  });

  it('Tests Attributes', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    // add definition
    cy.pressMainNavButton('Group Settings');
    cy.inMainNavFlyin('app-settings', () => {
      cy.get('button[data-cy^="Instance Attribute"]').click();
    });
    cy.inMainNavFlyin('app-attribute-definitions', () => {
      cy.get('button[data-cy^="Add"]').click();
      cy.contains('app-bd-notification-card', 'Add Definition').within(() => {
        cy.fillFormInput('id', 'DemoAttr');
        cy.fillFormInput('description', 'Demo Attribute');
        cy.get('button[data-cy="APPLY"]').click();
      });

      cy.contains('tr', 'DemoAttr').should('exist');
    });

    // now use definition in instance
    cy.inMainNavContent(() => {
      cy.get('app-configuration').should('exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should('exist');

      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Instance Attribute"]').click();
    });

    cy.inMainNavFlyin('app-attributes', () => {
      cy.get('button[data-cy^="Add/Edit"]').click();
      cy.contains('app-bd-notification-card', 'Add/Edit').within(() => {
        cy.fillFormSelect('id', 'Demo Attribute');
        cy.fillFormInput('value', 'Instance Value');
        cy.get('button[data-cy="APPLY"]').click();
      });

      cy.contains('tr', 'Instance Value').should('exist');
    });

    cy.pressMainNavButton('Instances');
    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Data Grouping');
    });

    cy.contains('mat-card', 'Grouping Level').within(() => {
      // this is NOT a bd-form-select
      cy.get('mat-select').should('exist').click();
      // escape all .within scopes to find the global overlay content
      cy.document().its('body').find('.cdk-overlay-container').contains('mat-option', 'Demo Attribute').should('exist').click();
    });

    cy.get('.cdk-overlay-backdrop-showing').click('top');

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Instance Value').should('exist');
      cy.contains('tr', instanceName).should('exist');
    });

    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.get('app-configuration').should('exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should('exist');

      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Instance Attribute"]').click();
    });

    cy.inMainNavFlyin('app-attributes', () => {
      cy.contains('tr', 'Instance Value')
        .should('exist')
        .within(() => {
          cy.get('button[data-cy^="Remove"]').click();
        });
      cy.contains('tr', 'Instance Value').should('not.exist');
    });
  });

  it('Cleans up', () => {
    cy.deleteGroup(groupName);
  });
});
