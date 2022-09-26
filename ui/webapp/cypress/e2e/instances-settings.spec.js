//@ts-check

const { group } = require('console');

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
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should(
        'exist'
      );

      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy="Base Configuration..."]').click();
    });

    cy.inMainNavFlyin('app-edit-config', () => {
      cy.fillFormInput('name', `${instanceName} (*)`);
      cy.fillFormInput('description', `${instanceName} (*)`);
      cy.fillFormSelect('purpose', 'DEVELOPMENT');
      cy.get('button[data-cy="Apply"]').click();
    });

    cy.inMainNavContent(() => {
      cy.contains('mat-toolbar', `Configuration - ${instanceName} (*)`).should(
        'exist'
      );
      cy.pressToolbarButton('Local Changes');
    });

    cy.inMainNavFlyin('app-local-changes', () => {
      cy.get('button[data-cy^="Discard"]').click();
      cy.contains('app-bd-notification-card', 'Discard').within(() => {
        cy.get('button[data-cy="Yes"]').click();
      });

      cy.get('button[data-cy="Close"]').click();
    });

    cy.checkMainNavFlyinClosed();

    cy.inMainNavContent(() => {
      cy.contains('mat-toolbar', `Configuration - ${instanceName} (*)`).should(
        'not.exist'
      );
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should(
        'exist'
      );
    });
  });

  it('Tests Instance Variables', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.get('app-configuration').should('exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should(
        'exist'
      );

      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Instance Variables"]').click();
    });

    cy.inMainNavFlyin('app-instance-variables', () => {
      cy.get('button[data-cy^="Add"]').click();
      cy.contains('app-bd-notification-card', 'Add Variable').within(() => {
        cy.fillFormInput('id', 'io.bdeploy.var');
        cy.fillFormInput('value_val', 'TheValue');
        cy.fillFormInput('description', 'The Description');
      });
    });

    cy.screenshot('Doc_InstVar_Plain');

    cy.inMainNavFlyin('app-instance-variables', () => {
      cy.contains('app-bd-notification-card', 'Add Variable').within(() => {
        cy.get('button[data-cy^=OK]').click();
      });

      cy.get('button[data-cy^="Add"]').click();
      cy.contains('app-bd-notification-card', 'Add Variable').within(() => {
        cy.fillFormInput('id', 'io.bdeploy.ref');
        cy.get('app-bd-form-input[name="value_val"]').within(() => {
          cy.contains('mat-button-toggle', 'data_object').click();
        });
        cy.get('app-bd-form-input[name="value_link"]').should('exist').click();
      });
    });

    cy.screenshot('Doc_InstVar_Link');

    cy.inMainNavFlyin('app-instance-variables', () => {
      cy.contains('app-bd-notification-card', 'Add Variable').within(() => {
        cy.fillFormInput('value_link', '{{X:io.bdeploy.var}}');
        cy.fillFormInput('description', 'Reference to the other var.');

        cy.get('button[data-cy^=OK]').click();
      });

      cy.get('button[data-cy^="Apply"]').click();
    });

    // Add Server Process
    cy.inMainNavContent(() => {
      cy.get('app-configuration').should('exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should(
        'exist'
      );
      cy.get('app-config-node[data-cy="master"]').within((node) => {
        cy.get('button[data-cy^="Add Application"]').click();
      });
    });

    cy.inMainNavFlyin('app-add-process', () => {
      cy.contains('tr', 'Server Application')
        .find('button[data-cy^="Add Server"]')
        .click();
      cy.waitUntilContentLoaded();
      cy.pressToolbarButton('Close');
    });

    cy.checkMainNavFlyinClosed();

    // Configure Server Process
    cy.inMainNavContent(() => {
      cy.get('app-config-node[data-cy="master"]').within((node) => {
        cy.contains('tr', 'Server Application').click();
      });
    });

    cy.inMainNavFlyin('app-edit-process-overview', () => {
      cy.get('button[data-cy^="Configure Parameters"]').click();
    });

    cy.inMainNavFlyin('app-configure-process', () => {
      // add optional sleep parameter and set value
      cy.contains('mat-expansion-panel', 'Test Parameters').within(() => {
        cy.get('mat-panel-title').click();
        cy.get('mat-expansion-panel-header').should(
          'have.attr',
          'aria-expanded',
          'true'
        );

        cy.get('button[data-cy^="Select Parameters"]').click();

        cy.get('div[data-cy="param.text"]').within(() => {
          cy.get('input[name="param.text_val"]').should('be.disabled');
          cy.contains('mat-icon', 'add').click();
          cy.contains('mat-icon', 'delete').should('exist');
        });

        cy.get('button[data-cy^="Confirm"]').click();

        cy.get('div[data-cy="param.text"]').within(() => {
          cy.get('input[name="param.text_val"]').should('be.enabled');

          cy.get('app-bd-form-input[name="param.text_val"]').within(() => {
            cy.contains('mat-button-toggle', 'data_object').click();
          });
          cy.get('app-bd-form-input[name="param.text_link"]')
            .should('exist')
            .click();
        });
      });
    });

    cy.screenshot('Doc_InstVar_InParameter');

    cy.inMainNavFlyin('app-configure-process', () => {
      cy.contains('mat-expansion-panel', 'Test Parameters').within(() => {
        cy.get('div[data-cy="param.text"]').within(() => {
          cy.fillFormInput('param.text_link', '{{X:io.bdeploy.x}}');

          cy.contains('unresolvable').should('exist');

          cy.fillFormInput('param.text_link', '{{X:io.bdeploy.ref}}');
        });
      });

      cy.get('button[data-cy^=Apply]').should('be.enabled').click();
    });
  });

  it('Tests Configuration Files', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.get('app-configuration').should('exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should(
        'exist'
      );

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

      cy.contains('app-bd-notification-card', 'Delete dummy2.cfg').within(
        () => {
          cy.get('button[data-cy="Yes"]').click();
        }
      );

      cy.waitUntilContentLoaded();

      cy.contains('tr', 'dummy2.cfg').should('not.exist');

      cy.contains('tr', 'binary.cfg').within(() => {
        cy.get('button[data-cy="Edit"]').should('be.disabled');
        cy.get('button[data-cy="Rename"]').click();
      });

      cy.contains('app-bd-notification-card', 'Rename binary.cfg').within(
        () => {
          cy.fillFormInput('newName', 'binary2.cfg');
          cy.get('button[data-cy="Confirm"]').should('be.enabled').click();
        }
      );
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavFlyin('app-config-files', () => {
      cy.pressToolbarButton('Back to Overview');
    });

    // potential validation still running.
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.get('button[data-cy^="Save"]').should('be.enabled').click();
      cy.waitUntilContentLoaded();
    });

    // save navigates to dashboard, navigate back
    cy.pressMainNavButton('Instance Configuration');
    cy.waitUntilContentLoaded();

    cy.pressToolbarButton('Instance Settings');

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

            cy.get('button[data-cy^="Create from product"]')
              .should('exist')
              .and('be.enabled')
              .click();
          });
      });

      cy.contains('tr', 'dummy1.cfg').within(() => {
        cy.get('button[data-cy="Edit"]').click();
      });
    });

    cy.waitUntilContentLoaded();

    cy.monacoEditor().should('contain.value', 'dummy configuration');
    cy.typeInMonacoEditor('Configuration File Content', true);

    cy.inMainNavFlyin('app-editor', () => {
      cy.pressToolbarButton('Apply');
    });

    cy.screenshot('Doc_InstanceConfigFiles');

    cy.inMainNavFlyin('app-config-files', () => {
      cy.pressToolbarButton('Add File');
      cy.contains('app-bd-notification-card', 'Add Configuration File').within(
        () => {
          cy.fillFormInput('path', 'test.json');
          cy.get('button[data-cy="OK"]').click();
        }
      );

      cy.waitUntilContentLoaded();

      cy.contains('tr', 'test.json')
        .should('exist')
        .within(() => {
          cy.get('button[data-cy="Edit"]').click();
        });
    });

    cy.typeInMonacoEditor('{{}{enter}"json" : "content"');

    cy.screenshot('Doc_InstanceConfigFilesEdit');

    cy.inMainNavFlyin('app-editor', () => {
      cy.pressToolbarButton('Apply');
    });

    cy.waitUntilContentLoaded();

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
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should(
        'exist'
      );

      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Banner"]').click();
    });

    cy.inMainNavFlyin('app-banner', () => {
      cy.get('textarea').clear().type('This is a banner text');
      cy.contains('app-bd-expand-button', 'Color')
        .click()
        .within(() => {
          cy.contains('app-color-select', 'Positive').click();
        })
        .click('top');

      cy.get('app-bd-banner').within(() => {
        cy.get('.local-banner').should(
          'have.css',
          'background-color',
          'rgb(46, 125, 50)'
        );
        cy.get('.local-banner').should(
          'have.css',
          'color',
          'rgb(255, 255, 255)'
        );
        cy.contains('This is a banner text').should('exist');
      });
    });

    cy.screenshot('Doc_InstanceBannerConfig');

    cy.inMainNavFlyin('app-banner', () => {
      cy.get('button[data-cy="Apply"]').should('be.enabled').click();
    });

    cy.inMainNavContent(() => {
      cy.get('app-bd-banner').within(() => {
        cy.get('.local-banner').should(
          'have.css',
          'background-color',
          'rgb(46, 125, 50)'
        );
        cy.get('.local-banner').should(
          'have.css',
          'color',
          'rgb(255, 255, 255)'
        );
        cy.contains('This is a banner text').should('exist');
      });
    });

    cy.screenshot('Doc_InstanceBanner');

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Banner"]').click();
    });

    cy.inMainNavFlyin('app-banner', () => {
      cy.get('button[data-cy^="Remove"]').should('be.enabled').click();
      cy.get('button[data-cy^="Remove"]').should('be.disabled');

      cy.get('app-bd-banner').within(() => {
        cy.get('.local-banner').should(
          'not.have.css',
          'background-color',
          'rgb(46, 125, 50)'
        );
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
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should(
        'exist'
      );

      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Manage Network Ports"]').click();
    });

    cy.inMainNavFlyin('app-ports', () => {
      cy.get('button[data-cy^="Export"]').downloadByLinkClick('ports.csv');
      cy.readFile(Cypress.config('downloadsFolder') + '/ports.csv').then(
        (content) => {
          expect(content).to.contain('Application,Name,Description,Port');
        }
      );
    });
  });

  it('Tests Nodes', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.get('app-configuration').should('exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should(
        'exist'
      );

      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Manage Nodes"]').click();
    });

    cy.screenshot('Doc_InstanceManageNodes');

    cy.inMainNavContent(() => {
      cy.contains('app-config-node', 'master')
        .should('exist')
        .within(() => {
          cy.contains('No data').should('exist');
        });
    });

    cy.inMainNavFlyin('app-nodes', () => {
      cy.get('button[data-cy="Apply"]').should('be.disabled');
      cy.contains('tr', 'master')
        .should('exist')
        .within(() => {
          cy.get('input[type="checkbox"]').uncheck({ force: true });
        });
      cy.get('button[data-cy="Apply"]').should('be.enabled').click();
    });

    cy.inMainNavContent(() => {
      cy.contains('app-config-node', 'master').should('not.exist');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Manage Nodes"]').click();
    });

    cy.inMainNavFlyin('app-nodes', () => {
      cy.get('button[data-cy="Apply"]').should('be.disabled');
      cy.contains('tr', 'master')
        .should('exist')
        .within(() => {
          cy.get('input[type="checkbox"]').check({ force: true });
        });
      cy.get('button[data-cy="Apply"]').should('be.enabled').click();
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
    cy.visit('/');
    cy.enterGroup(groupName);

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Group Settings');
    });

    // add definition
    cy.inMainNavFlyin('app-settings', () => {
      cy.get('button[data-cy^="Instance Attribute"]').click();
    });
    cy.inMainNavFlyin('app-attribute-definitions', () => {
      cy.get('button[data-cy^="Add"]').click();
      cy.contains('app-bd-notification-card', 'Add Definition').within(() => {
        cy.fillFormInput('id', 'DemoAttr');
        cy.fillFormInput('description', 'Demo Attribute');
        cy.get('button[data-cy="Apply"]').click();
      });

      cy.contains('tr', 'DemoAttr').should('exist');
    });

    // now use definition in instance
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();
    cy.inMainNavContent(() => {
      cy.get('app-configuration').should('exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should(
        'exist'
      );

      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Instance Attribute"]').click();
    });

    cy.inMainNavFlyin('app-attributes', () => {
      cy.waitUntilContentLoaded();
      cy.get('button[data-cy^="Add/Edit"]').click();
      cy.contains('app-bd-notification-card', 'Add/Edit').within(() => {
        cy.fillFormSelect('id', 'Demo Attribute');
        cy.fillFormInput('value', 'Instance Value');
        cy.get('button[data-cy="Apply"]').click();
      });

      cy.contains('tr', 'Instance Value').should('exist');
    });

    cy.pressMainNavButton('Instances');
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
        .contains('mat-option', 'Demo Attribute')
        .should('exist')
        .click();
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
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should(
        'exist'
      );

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
