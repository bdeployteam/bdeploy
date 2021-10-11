//@ts-check

const { validateZip } = require('../support/utils');

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
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');
    cy.createInstance(groupName, instanceName, 'Demo Product', '2.0.0');
  });

  it('Tests Instance Templates', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    // shortcut to get to the templates
    cy.inMainNavContent(() => {
      cy.contains('.bd-rect-card', 'The instance is currently empty')
        .should('exist')
        .within(() => {
          cy.get('button[data-cy^="Apply Instance Template"]').click();
        });
    });

    cy.inMainNavFlyin('app-instance-templates', () => {
      cy.contains('tr', 'Default Configuration').should('exist');
      cy.pressToolbarButton('Back to Overview');
    });

    // "normal" way to get to the templates
    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Instance Templates"]').click();
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavFlyin('app-instance-templates', () => {
      cy.contains('tr', 'Default Configuration')
        .should('exist')
        .within(() => {
          cy.get('button').click();
        });

      cy.contains('app-bd-notification-card', 'Assign Template').within(() => {
        cy.fillFormSelect('Server Apps', 'Apply to master');
        cy.fillFormSelect('Client Apps', '(skip)');

        cy.get('button[data-cy="Confirm"]').click();
      });

      cy.contains('app-bd-notification-card', 'Assign Variable Values').within(() => {
        cy.fillFormInput('Text Value', 'Test Text');
        cy.fillFormInput('Sleep Timeout', '5');

        cy.get('button[data-cy="Confirm"]').click();
      });
    });

    cy.inMainNavContent(() => {
      cy.contains('app-config-node', 'master')
        .should('exist')
        .within(() => {
          cy.contains('tr', 'Server No Sleep').should('exist');
          cy.contains('tr', 'Server With Sleep').should('exist');
          cy.contains('tr', 'Another Server With Sleep').should('exist').click();
        });
    });

    cy.inMainNavFlyin('app-edit-process-overview', () => {
      cy.get('button[data-cy^="Configure Parameters"]').click();
    });

    cy.inMainNavFlyin('app-configure-process', () => {
      cy.get('app-bd-form-input[name="name"]').within(() => {
        cy.get('input').should('have.value', 'Another Server With Sleep');
        cy.get('input').clear().type('Another Process'); // avoid name problems in test.
      });
      cy.contains('mat-expansion-panel', 'Sleep Configuration').within(() => {
        cy.get('mat-panel-title').click();
        cy.get('mat-expansion-panel-header').should('have.attr', 'aria-expanded', 'true');

        cy.get('app-bd-form-input[name="param.sleep"]')
          .should('exist')
          .within(() => {
            cy.get('input').should('have.value', '5');
          });
      });

      // intentionally not using apply, should prompt to save on leave
      cy.pressToolbarButton('Back to Overview');

      cy.contains('app-bd-notification-card', 'Save Changes').within(() => {
        cy.get('button[data-cy="Save"]').click();
      });
    });

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Another Process').should('exist');
      cy.waitForApi(() => {
        cy.pressToolbarButton('Save');
      });

      cy.waitUntilContentLoaded();
    });
  });

  it('Tests Process Templates', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('app-config-node', 'master').within(() => {
        cy.contains('tr', 'Server With Sleep').click();
      });
    });

    cy.inMainNavFlyin('app-edit-process-overview', () => {
      cy.get('button[data-cy="Delete"]').click();
    });

    cy.inMainNavContent(() => {
      cy.contains('app-config-node', 'master').within(() => {
        cy.contains('tr', 'Server With Sleep').should('not.exist');
        cy.get('button[data-cy^="Add Application"]').click();
      });
    });

    cy.inMainNavFlyin('app-add-process', () => {
      cy.contains('tr', 'Server With Sleep')
        .should('exist')
        .within(() => {
          cy.get('button[data-cy^="Add template"]').click();
        });

      cy.contains('app-bd-notification-card', 'Assign Variable Values').within(() => {
        cy.fillFormInput('Sleep Timeout', '7');
        cy.get('button[data-cy="Confirm"]').click();
      });
    });

    cy.inMainNavContent(() => {
      cy.contains('app-config-node', 'master').within(() => {
        cy.contains('tr', 'Server With Sleep').should('exist').click();
      });
    });

    cy.inMainNavFlyin('app-edit-process-overview', () => {
      cy.get('button[data-cy^="Configure Parameters"]').click();
    });

    cy.inMainNavFlyin('app-configure-process', () => {
      cy.get('app-bd-form-input[name="name"]').within(() => {
        cy.get('input').should('have.value', 'Server With Sleep');
      });
      cy.contains('mat-expansion-panel', 'Sleep Configuration').within(() => {
        cy.get('mat-panel-title').click();
        cy.get('mat-expansion-panel-header').should('have.attr', 'aria-expanded', 'true');

        cy.get('app-bd-form-input[name="param.sleep"]')
          .should('exist')
          .within(() => {
            cy.get('input').should('have.value', '7');
          });
      });
      cy.pressToolbarButton('Back to Overview');
    });
  });

  it('Tests Instance Export', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance History');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Current').click();
    });

    cy.inMainNavFlyin('app-history-entry', () => {
      cy.get('button[data-cy^="Export"]').downloadByLocationAssign('instance-export.zip', true);
      validateZip('instance-export.zip', 'instance.json', true);
    });
  });

  it('Tests Instance Import', () => {
    cy.createInstance(groupName, instanceName + '2', 'Demo Product', '2.0.0');
    cy.enterInstance(groupName, instanceName + '2');

    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('Current Version: 1').should('exist');
      cy.contains('.bd-rect-card', 'The instance is currently empty').should('exist');
      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Import"]').click();
    });

    cy.inMainNavFlyin('app-import-instance', () => {
      cy.fillFileDrop('instance-export.zip');
      cy.contains('app-bd-notification-card', 'Success').should('exist');
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('Current Version: 2').should('exist');
      cy.contains('app-config-node', 'master').within(() => {
        cy.contains('tr', 'Server No Sleep').should('exist');
        cy.contains('tr', 'Server With Sleep').should('exist');
        cy.contains('tr', 'Another Process').should('exist');
      });
    });

    // test local modification when importing
    cy.inMainNavFlyin('app-import-instance', () => {
      cy.pressToolbarButton('Back to Overview');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Base Configuration"]').click();
    });

    cy.inMainNavFlyin('app-edit-config', () => {
      cy.fillFormInput('description', 'Modified Description');
      cy.get('button[data-cy="Apply"]').click();
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Import"]').click();
    });

    cy.inMainNavFlyin('app-import-instance', () => {
      cy.fillFileDrop('instance-export.zip');
      cy.contains('app-bd-notification-card', 'Success').should('exist');
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('Current Version: 3').should('exist');
      cy.contains('SAVING NOT POSSIBLE').should('exist');
    });
  });

  it('Cleans up', () => {
    cy.deleteGroup(groupName);
  });
});
