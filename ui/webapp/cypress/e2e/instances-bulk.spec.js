describe('Instance Bulk Tests', () => {
  var groupName = 'Demo';

  beforeEach(() => {
    cy.login();
  });

  it('Prepares the test (group, products, instances)', () => {
    cy.visit('/');
    cy.createGroup(groupName);

    cy.visit('/');
    cy.uploadProductIntoGroup(groupName, 'test-product-1-direct.zip');

    cy.visit('/');
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');

    cy.visit('/');
    cy.createInstance(groupName, 'Demo A', 'Demo Product', '1.0.0');

    cy.visit('/');
    cy.createInstance(groupName, 'Demo B', 'Demo Product', '1.0.0');

    cy.visit('/');
    cy.createInstance(groupName, 'Demo C', 'Demo Product', '1.0.0');
  });

  it('Bulk install / activate', () => {
    cy.visit('/');
    cy.enterGroup(groupName);

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Bulk Manipulation');

      cy.contains('tr', 'Demo A')
        .find('input[type=checkbox]')
        .check({ force: true });
      cy.contains('tr', 'Demo B')
        .find('input[type=checkbox]')
        .check({ force: true });
      cy.contains('tr', 'Demo C')
        .find('input[type=checkbox]')
        .check({ force: true });
    });

    cy.inMainNavFlyin('app-bulk-manipulation', () => {
      cy.contains('div', 'instances selected')
        .find('strong:contains("3")')
        .should('exist');

      cy.get('button[data-cy^=Install]').should('be.enabled').click();

      cy.contains('app-bd-notification-card', 'Result').within(() => {
        cy.get('span:contains("Installed")').should('have.length', 3);
        cy.get('button[data-cy=OK]').click();
      });
    });

    cy.inMainNavFlyin('app-bulk-manipulation', () => {
      cy.get('button[data-cy^=Activate]').should('be.enabled').click();

      cy.contains('app-bd-notification-card', 'Activate').within(() => {
        cy.fillFormInput('confirm', 'I UNDERSTAND');
        cy.get('button[data-cy=Yes]').click();
      });

      cy.contains('app-bd-notification-card', 'Result').within(() => {
        cy.get('span:contains("Activated")').should('have.length', 3);
        cy.get('button[data-cy=OK]').click();
      });
    });
  });

  it('Bulk start / stop', () => {
    cy.visit('/');
    cy.enterGroup(groupName);

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Bulk Manipulation');

      cy.contains('tr', 'Demo A')
        .find('input[type=checkbox]')
        .check({ force: true });
      cy.contains('tr', 'Demo B')
        .find('input[type=checkbox]')
        .check({ force: true });
      cy.contains('tr', 'Demo C')
        .find('input[type=checkbox]')
        .check({ force: true });
    });

    cy.inMainNavFlyin('app-bulk-manipulation', () => {
      cy.contains('div', 'instances selected')
        .find('strong:contains("3")')
        .should('exist');

      cy.get('button[data-cy^=Start]').should('be.enabled').click();
      cy.get('button[data-cy^=Stop]').should('be.enabled').click();
    });
  });

  it('Bulk set product', () => {
    cy.visit('/');
    cy.enterGroup(groupName);

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Bulk Manipulation');

      cy.contains('tr', 'Demo A')
        .find('input[type=checkbox]')
        .check({ force: true });
      cy.contains('tr', 'Demo B')
        .find('input[type=checkbox]')
        .check({ force: true });
      cy.contains('tr', 'Demo C')
        .find('input[type=checkbox]')
        .check({ force: true });
    });

    cy.inMainNavFlyin('app-bulk-manipulation', () => {
      cy.contains('div', 'instances selected')
        .find('strong:contains("3")')
        .should('exist');

      cy.get('button[data-cy^="Set Product Version"]')
        .should('be.enabled')
        .click();

      cy.contains(
        'app-bd-notification-card',
        'Choose Target Product Version'
      ).within(() => {
        cy.fillFormSelect('prodVersion', '1.0.0');
        cy.get('button[data-cy=Apply]').click();
      });

      cy.contains('app-bd-notification-card', 'Result').within(() => {
        cy.get('span:contains("Skipped")').should('have.length', 3);
        cy.get('button[data-cy=OK]').click();
      });

      cy.get('button[data-cy^="Set Product Version"]')
        .should('be.enabled')
        .click();

      cy.contains(
        'app-bd-notification-card',
        'Choose Target Product Version'
      ).within(() => {
        cy.fillFormSelect('prodVersion', '2.0.0');
        cy.get('button[data-cy=Apply]').click();
      });

      cy.contains('app-bd-notification-card', 'Result').within(() => {
        cy.get('span:contains("Created instance version")').should(
          'have.length',
          3
        );
        cy.get('button[data-cy=OK]').click();
      });
    });
  });

  it('Bulk delete', () => {
    cy.visit('/');
    cy.enterGroup(groupName);

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Bulk Manipulation');

      cy.contains('tr', 'Demo A')
        .find('input[type=checkbox]')
        .check({ force: true });
      cy.contains('tr', 'Demo B')
        .find('input[type=checkbox]')
        .check({ force: true });
    });

    cy.inMainNavFlyin('app-bulk-manipulation', () => {
      cy.contains('div', 'instances selected')
        .find('strong:contains("2")')
        .should('exist');

      cy.get('button[data-cy^="Delete"]').should('be.enabled').click();

      cy.contains('app-bd-notification-card', 'Delete').within(() => {
        cy.fillFormInput('confirm', 'I UNDERSTAND');
        cy.get('button[data-cy=Yes]').click();
      });

      cy.contains('app-bd-notification-card', 'Result').within(() => {
        cy.get('span:contains("Deleted")').should('have.length', 2);
        cy.get('button[data-cy=OK]').click();
      });
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Demo A').should('not.exist');
      cy.contains('tr', 'Demo B').should('not.exist');
      cy.contains('tr', 'Demo C').should('exist');
    });
  });
});
