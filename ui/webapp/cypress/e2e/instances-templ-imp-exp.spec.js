//@ts-check

const { validateZip } = require('../support/utils');

describe('Instance Settings Tests', () => {
  var groupName = 'Demo';
  var instanceName = 'TestInstance';

  beforeEach(() => {
    cy.login();
  });

  it('Prepares the test (group, products, instance)', () => {
    cy.visit('/');
    cy.createGroup(groupName);

    cy.visit('/');
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');

    cy.visit('/');
    cy.createInstance(groupName, instanceName, 'Demo Product', '2.0.0');
  });

  it('Tests Instance Templates', () => {
    cy.visit('/');
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    // shortcut to get to the templates
    cy.inMainNavContent(() => {
      cy.contains('.bd-rect-card', 'The instance is currently empty')
        .should('exist')
        .within(() => {
          cy.get('button[data-cy^="Apply Instance Template"]').click();
        });
    });

    cy.screenshot('Doc_InstanceTemplates');

    cy.inMainNavFlyin('app-instance-templates', () => {
      cy.pressToolbarButton('Back to Overview');
    });

    // "normal" way to get to the templates
    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Instance Templates"]').click();
    });

    cy.inMainNavFlyin('app-instance-templates', () => {
      cy.fillFormSelect('Template', 'Default Configuration');
    });

    cy.screenshot('Doc_InstanceTemplatesNodes');

    cy.inMainNavFlyin('app-instance-templates', () => {
      cy.fillFormSelect('Server Apps', 'Apply to master');
      cy.fillFormSelect('Client Apps', '(skip)');
      cy.get('button[data-cy="Next"]').click();
    });

    cy.screenshot('Doc_InstanceTemplatesVars');

    cy.inMainNavFlyin('app-instance-templates', () => {
      cy.fillFormInput('Text Value', 'TestText');
      cy.fillFormInput('Sleep Timeout', '5');
      cy.get('button[data-cy="Confirm"]').click();
    });

    cy.inMainNavContent(() => {
      cy.contains('app-config-node', 'master')
        .should('exist')
        .within(() => {
          cy.contains('tr', 'Server No Sleep').should('exist');
          cy.contains('tr', 'Server With Sleep').should('exist');
        });
    });

    cy.screenshot('Doc_InstanceTemplatesDone');

    // edit control group
    cy.inMainNavContent(() => {
      cy.get('app-config-node[data-cy="master"]').within((node) => {
        cy.contains('app-control-group', 'First Group').within(() => {
          cy.get('button[data-cy^="Edit Control Group"]').click();
        });
      });
    });

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_InstanceConfigEditProcessControlGroup');

    cy.inMainNavFlyin('app-edit-control-group', () => {
      cy.pressToolbarButton('Close');
    });

    // continue with parameter configuration.
    cy.inMainNavContent(() => {
      cy.contains('app-config-node', 'master').within(() => {
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

        cy.get('app-bd-form-input[name="param.sleep_link"]')
          .should('exist')
          .within(() => {
            cy.get('input[name="param.sleep_link"]').should('have.value', '{{X:param.shared.exp}}');
          });
      });

      // need to make sure no validation is running anymore.
      cy.get('app-bd-dialog-toolbar').within(() => {
        cy.get(`button[data-cy^="Apply"]`).should('be.enabled');
      });

      // intentionally not using apply, should prompt to save on leave
      cy.pressToolbarButton('Back to Overview');

      cy.contains('app-bd-notification-card', 'Save Changes').within(() => {
        cy.get('button[data-cy="Save"]').click();
      });
    });

    // the param.shared instance variable should have been created by the template - adapt it to '7'.
    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Instance Variables"]').click();
    });

    cy.inMainNavFlyin('app-instance-variables', () => {
      // check that instance variable values have been applied correctly
      cy.contains('mat-expansion-panel', 'Instance Variable Definitions').within(() => {
        cy.contains('mat-panel-description', '2/2 variables shown').should('exist');
        cy.get('mat-panel-title').click();
        cy.get('mat-expansion-panel-header').should('have.attr', 'aria-expanded', 'true');

        cy.get('app-bd-form-input[name="instance.variable.v1_val"]').within(() => {
          cy.get('input[name="instance.variable.v1_val"]').should('have.value', 'value-v1');
          cy.get('mat-error')
            .contains('Instance Variable Definition 1 value does not match regex: ^[a-zA-Z]+$')
            .should('exist');
          cy.get('input[name="instance.variable.v1_val"]').clear().type('valuevone');
          cy.get('mat-error').should('not.exist');
        });
        cy.get('app-bd-form-input[name="instance.variable.v2_val"]').within(() => {
          cy.get('input[name="instance.variable.v2_val"]').should('have.value', 'TestText');
        });
        cy.get('mat-panel-title').click();
      });

      cy.contains('mat-expansion-panel', 'Product Description Variables').within(() => {
        cy.contains('mat-panel-description', '3/3 variables shown').should('exist');
        cy.get('mat-panel-title').click();
        cy.get('app-bd-form-input[name="product.instance.variable1_val"]').within(() => {
          cy.get('input[name="product.instance.variable1_val"]').should('have.value', '2.0.0');
        });
        cy.get('app-bd-form-input[name="product.instance.variable2_val"]').within(() => {
          cy.get('input[name="product.instance.variable2_val"]').should('have.value', 'admin');
        });
        cy.get('app-bd-form-toggle[name="product.instance.licensed_bool"]').within(() => {
          cy.get('input[type="checkbox"]').should('not.be.checked');
        });
        cy.get('mat-panel-title').click();
      });

      cy.contains('mat-expansion-panel', 'Custom Variables').within(() => {
        cy.contains('mat-panel-description', '3/3 variables shown').should('exist');
        cy.get('mat-panel-title').click();
        cy.get('mat-expansion-panel-header').should('have.attr', 'aria-expanded', 'true');

        cy.get('app-bd-form-input[name="param.global_val"]').within(() => {
          cy.get('input[name="param.global_val"]').should('have.value', 'TestText');
        });
        cy.get('app-bd-form-input[name="param.xshared_val"]').within(() => {
          cy.get('input[name="param.xshared_val"]').should('have.value', '5');
          cy.contains('mat-icon', 'edit').click({ force: true });
        });
        cy.get('app-bd-form-input[name="param.shared.exp_link"]').within(() => {
          cy.get('input[name="param.shared.exp_link"]').should('have.value', '{{X:param.xshared}}');
        });
      });

      cy.contains('app-bd-notification-card', 'Edit Variable').within(() => {
        cy.fillFormInput('value_val', '7');
        cy.fillFormInput('description', 'Modified shared instance variable');

        cy.get('button[data-cy^=OK]').click();
      });

      cy.get('button[data-cy^="Apply"]').click();
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
    cy.visit('/');
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

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

    cy.screenshot('Doc_InstanceAddProcessPanel');

    cy.inMainNavFlyin('app-add-process', () => {
      cy.contains('tr', 'Server With Sleep')
        .should('exist')
        .within(() => {
          cy.get('button[data-cy^="Add template"]').click();
        });
    });

    cy.screenshot('Doc_InstanceAddProcessTemplVars');

    cy.inMainNavFlyin('app-add-process', () => {
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

    cy.screenshot('Doc_InstanceNewProcess');

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

        cy.get('app-bd-form-input[name="param.sleep_val"]')
          .should('exist')
          .within(() => {
            cy.get('input[name="param.sleep_val"]').should('have.value', '7');
          });
      });
      cy.pressToolbarButton('Back to Overview');
    });
  });

  it('Tests Instance Export', () => {
    cy.visit('/');
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance History');

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Current').click();
    });

    cy.inMainNavFlyin('app-history-entry', () => {
      cy.get('button[data-cy^="Export"]').downloadByLocationAssign('instance-export.zip', true);
      validateZip('instance-export.zip', 'instance.json', true);
    });
  });

  it('Tests Instance Import', () => {
    cy.visit('/');
    cy.createInstance(groupName, instanceName + '2', 'Demo Product', '2.0.0');

    cy.visit('/');
    cy.enterInstance(groupName, instanceName + '2');

    cy.pressMainNavButton('Instance Configuration');

    cy.inMainNavContent(() => {
      cy.contains('Current Version: 1').should('exist');
      cy.contains('.bd-rect-card', 'The instance is currently empty').should('exist');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Import"]').click();
    });

    cy.inMainNavFlyin('app-import-instance', () => {
      cy.fillFileDrop('instance-export.zip');
      cy.contains('app-bd-notification-card', 'Success').should('exist');
    });

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

    cy.inMainNavContent(() => {
      cy.contains('Current Version: 3').should('exist');
      cy.contains('SAVING NOT POSSIBLE').should('exist');
    });
  });
});
