describe('BHive Browser Tests', function () {
  beforeEach(() => {
    cy.login();
  });

  it('Check user manifest in default hive', function () {
    cy.visit('/');
    cy.visit('/#/admin/all/(panel:hive)');

    // wait for at least one row, meaning the pre-selected hive has been loaded.
    cy.get('tbody>tr').should('exist');

    // all in one chain to re-try the click on the drop down if it does not open.
    cy.get('mat-select[placeholder="Select a Hive"]')
      .should('have.length', 1)
      .click()
      .get('body')
      .contains('mat-option', 'default')
      .click();

    cy.get('td:contains("users/admin")')
      .should('be.visible')
      .and('have.length.gte', 1)
      .contains('td', 'users/admin:1')
      .click();
    cy.get('td:contains("user.json")').should('have.length', 1);

    cy.contains('mat-chip', 'home').click();

    cy.get('td:contains("users/admin")').should('be.visible').and('have.length.gte', 1);
  });

  it('Check download of manifest', function () {
    cy.contains('td', 'users/admin')
      .siblings()
      .contains('mat-icon', 'cloud_download')
      .downloadBlobFileShould((resp) => {
        const json = JSON.parse(resp);
        expect(json).to.have.property('key');
        expect(json.key).to.have.property('name', 'users/admin');
        expect(json.key).to.have.property('tag');
      });
  });

  it('Check download of user json', function () {
    cy.contains('td', 'users/admin').click();
    cy.contains('td', 'user.json')
      .siblings()
      .contains('mat-icon', 'cloud_download')
      .downloadBlobFileShould((resp) => {
        const json = JSON.parse(resp);
        expect(json).to.have.property('name', 'admin');
        expect(json).to.have.property('password');
        expect(json).to.have.property('permissions');
      });
  });

  it('BHive FSCK', function () {
    cy.contains('button', 'bug_report').click();
    cy.contains('snack-bar-container', '0 damaged').should('exist').contains('button', 'DISMISS').click();
  });

  it('BHive Repair', function () {
    cy.contains('button', 'build').click();
    cy.contains('snack-bar-container', '0 damaged').should('exist').contains('button', 'DISMISS').click();
  });

  it('BHive Prune', function () {
    cy.contains('button', 'delete_sweep').click();
    cy.contains('snack-bar-container', 'Prune freed').should('exist').contains('button', 'DISMISS').click();
  });
});
