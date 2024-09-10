describe('System Template Tests', () => {
  var groupName = 'Demo';
  var repoName = 'DemoRepo';

  beforeEach(() => {
    cy.login();
  });

  it('Prepares the test (group, products)', () => {
    cy.visit('/');
    cy.createGroup(groupName);

    cy.visit('/');
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');

    cy.createRepo(repoName);

    cy.uploadProductIntoRepo(repoName, 'chat-product-1-direct.zip');
  });

  it('Creates Instances From System Template', () => {
    cy.visit('/');
    cy.enterGroup(groupName);

    cy.pressToolbarButton('Apply System Template');

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_SystemTemplate_Wizard');

    cy.inMainNavContent(() => {
      cy.get('[data-cy=step-template-upload]').within(() => {
        cy.contains('Drop System Template').should('exist');
        cy.fillFileDrop('system-template.yaml');
        cy.contains('Drop System Template').should('not.exist');

        cy.contains("Loaded 'Test System'").should('exist');
        cy.get('button[data-cy^=Next]').should('be.enabled').click();
      });

      cy.get('[data-cy=step-import-missing-products]').within(() => {
        cy.contains('1 product from the template is missing and will be imported automatically').should('exist');
        cy.contains('io.bdeploy/chat-app/product:1.0.0').should('exist');
        cy.get('button[data-cy^=Next]').should('be.enabled').click();
      });

      cy.get('[data-cy=step-name-purpose]').within(() => {
        cy.fillFormInput('name', 'Demo System');
        cy.fillFormSelect('purpose', 'TEST');

        cy.get('button[data-cy^=Next]').should('be.enabled').click();
      });

      cy.get('[data-cy=step-system-variables]').within(() => {
        cy.fillFormInput('The Node Number', '2');
        cy.fillFormInput('The Node Base Name', 'master');

        cy.get('button[data-cy^=Next]').should('be.enabled').click();
      });
    });

    cy.screenshot('Doc_SystemTemplate_InstanceTemplates');

    cy.inMainNavContent(() => {
      cy.get('[data-cy=step-instance-templates]').within(() => {
        cy.get('[data-cy="tab-Demo Instance"]').within(() => {
          cy.fillFormSelect('Server Apps', 'Apply to master');
          cy.fillFormSelect('Client Apps', 'Apply to Client Applications');

          cy.fillFormInput('Sleep Timeout', '10');
          cy.fillFormInput('Text Value', 'demo');
        });

        cy.get('button[data-cy^=Next]').should('be.enabled').click();
      });
    });

    cy.waitUntilContentLoaded();
    cy.inMainNavContent(() => {
      cy.get('[data-cy=step-done]').within(() => {
        cy.contains('Instances have been created').should('exist');
        cy.get('tr:contains("Successfully created instance")').should('have.length', 4);
      });
    });

    cy.screenshot('Doc_SystemTemplate_Done');
  });

  it('Checks Instances', () => {
    cy.visit('/');
    cy.enterGroup(groupName);

    cy.inMainNavContent(() => {
      cy.get('tr:contains("Demo Chat App")').should('have.length', 3);
      cy.get('tr:contains("Demo Product")').should('have.length', 1);

      cy.contains('Demo Instance').should('exist');
      cy.contains('Chat Node 3').should('exist');
      cy.contains('Chat Node 4').should('exist');
      cy.contains('Chat Master').should('exist');
    });
  });

  it('Checks System Variables', () => {
    cy.visit('/');
    cy.enterGroup(groupName);

    cy.pressMainNavButton('Systems');

    cy.inMainNavContent(() => {
      cy.get('tr:contains("Demo System")').should('exist').click();
    });

    cy.inMainNavFlyin('app-system-details', () => {
      cy.get('button[data-cy^="System Variables"]').should('exist').click();
    });

    cy.inMainNavFlyin('app-system-variables', () => {
      cy.contains('mat-expansion-panel', 'System Meta Information').within(() => {
        cy.contains('mat-panel-description', '1/1 variables shown').should('exist');
        cy.get('mat-panel-title').click();
        cy.get('app-bd-form-input[name="system.variable.count_val"]')
          .should('exist')
          .within(() => {
            cy.get('input[name="system.variable.count_val"]').should('have.value', '3');
          });
        cy.get('mat-panel-title').click();
      });

      cy.contains('mat-expansion-panel', 'System Variable Definitions').within(() => {
        cy.contains('mat-panel-description', '2/2 variables shown').should('exist');
        cy.get('mat-panel-title').click();
        cy.get('app-bd-form-input[name="system.variable.numeric_val"]')
          .should('exist')
          .within(() => {
            cy.get('input[name="system.variable.numeric_val"]').should('have.value', '1020');
          });
        cy.get('app-bd-form-input[name="system.variable.string_val"]')
          .should('exist')
          .within(() => {
            cy.get('input[name="system.variable.string_val"]').should('have.value', 'defaultDefinedV1');
          });
        cy.get('mat-panel-title').click();
      });

      cy.contains('mat-expansion-panel', 'Ungrouped Variables').within(() => {
        cy.contains('mat-panel-description', '1/1 variables shown').should('exist');
        cy.get('mat-panel-title').click();
        cy.get('app-bd-form-input[name="test.system.var_val"]')
          .should('exist')
          .within(() => {
            cy.get('input[name="test.system.var_val"]').should('have.value', 'testValue');
          });
        cy.get('mat-panel-title').click();
      });

      cy.contains('mat-expansion-panel', 'Custom Variables').within(() => {
        cy.contains('mat-panel-description', '0/0 variables shown').should('exist');
        cy.get('mat-panel-title').click();
        cy.get('button[data-cy="Add Custom Variable"]').click();
      });

      cy.contains('app-bd-notification-card', 'Add Variable').within(() => {
        cy.fillFormInput('id', 'io.bdeploy.systemvar');
        cy.fillFormInput('value_val', 'TheValue');
        cy.fillFormInput('description', 'The Description');
        cy.get('button[data-cy^=OK]').click();
      });

      cy.contains('mat-expansion-panel', 'Custom Variables').within(() => {
        cy.contains('mat-panel-description', '1/1 variables shown').should('exist');
      });

      cy.get('button[data-cy^="Save"]').click();

      cy.contains('app-bd-notification-card', 'Saving 4 instances').within(() => {
        cy.contains(
          'div',
          'Affected 4 will be updated with the new system version. This needs to be installed and activated on all affected instances.',
        );
        cy.get('button[data-cy^=Yes]').click();
      });
    });
  });

  it('Checks Instance Variables', () => {
    cy.visit('/');
    cy.enterInstance(groupName, 'Demo Instance');
    cy.pressMainNavButton('Instance Configuration');

    cy.pressMainNavButton('Instance Configuration');

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Instance Variables"]').click();
    });

    cy.contains('mat-expansion-panel', 'Custom Variables').within(() => {
      cy.contains('mat-panel-description', '3/3 variables shown').should('exist');
      cy.get('mat-panel-title').click();
      cy.get('app-bd-form-input[name="param.global_val"]').within(() => {
        cy.get('input[name="param.global_val"]').should('have.value', 'demo');
      });
      cy.get('app-bd-form-input[name="param.xshared_val"]').within(() => {
        cy.get('input[name="param.xshared_val"]').should('have.value', '10');
      });
      cy.get('mat-panel-title').click();
    });

    cy.contains('mat-expansion-panel', 'Ungrouped Variables').within(() => {
      cy.contains('mat-panel-description', '2/2 variables shown').should('exist');
      cy.get('mat-panel-title').click();
      cy.get('app-bd-form-input[name="product.instance.ungrouped.variable1_val"]').within(() => {
        cy.get('input[name="product.instance.ungrouped.variable1_val"]').should('have.value', '26.7.2024');
      });
      cy.get('app-bd-form-input[name="product.instance.ungrouped.variable2_val"]').within(() => {
        cy.get('input[name="product.instance.ungrouped.variable2_val"]').should('have.value', '27.7.2024');
      });
      cy.get('mat-panel-title').click();
    });

    cy.contains('mat-expansion-panel', 'Product Description Variables').within(() => {
      cy.contains('mat-panel-description', '3/3 variables shown').should('exist');
      cy.get('mat-panel-title').click();
      cy.get('app-bd-form-input[name="product.instance.variable3_val"]').within(() => {
        cy.get('input[name="product.instance.variable3_val"]').should('have.value', 'teenagers');
      });
      cy.get('app-bd-form-input[name="product.instance.variable2_val"]').within(() => {
        cy.get('input[name="product.instance.variable2_val"]').should('have.value', 'admin');
      });
      cy.get('mat-panel-title').click();
    });

    cy.contains('mat-expansion-panel', 'Instance Variable Definitions').within(() => {
      cy.contains('mat-panel-description', '2/2 variables shown').should('exist');
      cy.get('mat-panel-title').click();
      cy.get('app-bd-form-input[name="instance.variable.v1_val"]').within(() => {
        cy.get('input[name="instance.variable.v1_val"]').should('have.value', 'defaultDefinedV1');
      });
      cy.get('app-bd-form-input[name="instance.variable.v2_val"]').within(() => {
        cy.get('input[name="instance.variable.v2_val"]').should('have.value', 'defaultDefinedV2');
      });
      cy.get('mat-panel-title').click();
    });
  });
});
