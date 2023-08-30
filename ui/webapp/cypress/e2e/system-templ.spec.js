describe('System Template Tests', () => {
  var groupName = 'Demo';

  beforeEach(() => {
    cy.login();
  });

  it('Prepares the test (group, products)', () => {
    cy.visit('/');
    cy.createGroup(groupName);

    cy.visit('/');
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');

    cy.visit('/');
    cy.uploadProductIntoGroup(groupName, 'chat-product-1-direct.zip');
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
        cy.get('tr:contains("Successfully created instance")').should(
          'have.length',
          4
        );
      });
    });

    cy.screenshot('Doc_SystemTemplate_Done');
  });

  it('Check Instances', () => {
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
});
