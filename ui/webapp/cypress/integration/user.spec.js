describe('User Accounts Test', function() {

  var demoUser = 'demo-account';
  var mailDomain = '@example.com';

  this.beforeEach(function() {
    cy.login();
  })

  it('Enters the user accounts dialog and creates a test user', function() {
    cy.visit('/#/admin/all/(panel:users)');
    cy.waitUntilContentLoaded();

    cy.screenshot('BDeploy_UserAccounts');

    cy.createUser(demoUser, 'Demo account', demoUser + mailDomain, 'demo', true);
    cy.searchUser(demoUser);
  })

  it('edits the user', function() {
    cy.contains('tr', demoUser).should('exist');
    cy.contains('tr', demoUser).clickContextMenuItem('Edit...');

    cy.get('input[placeholder="Full Name"]').should('exist').click();
    cy.get('input[placeholder="Full Name"]').clear().type('John Doe');
    cy.get('input[placeholder="E-Mail Address"]').clear().type('john-doe' + mailDomain);

    cy.screenshot('BDeploy_UserAccounts_Edit');
    cy.contains('button', 'Apply').click();

    cy.contains('td', 'John Doe').should('exist');
    cy.contains('td', 'john-doe' + mailDomain).should('exist');
  })

  it('Deactivates the user', function() {
    cy.waitUntilContentLoaded();
    cy.contains('tr', demoUser).clickContextMenuItem('Set Inactive');
    cy.contains('td', 'check_box').should('exist');
    cy.screenshot('BDeploy_UserAccounts_Inactive');
  })

  it('Activates the user', function() {
    cy.waitUntilContentLoaded();
    cy.contains('tr', demoUser).clickContextMenuItem('Set Active');
    cy.contains('td', 'check_box').should('not.exist');
  })

  it('Sets a global capability for the user', function() {
    cy.waitUntilContentLoaded();
    cy.setGlobalCapability(demoUser, 'WRITE', true);
    cy.contains('td', 'WRITE').should('exist');
  })

  it('Removes the global capability for the user', function() {
    cy.waitUntilContentLoaded();
    cy.setGlobalCapability(demoUser, '(none)');
    cy.contains('td', 'WRITE').should('not.exist');
  })

  it('Deletes the user and resets the dialog', function() {
    cy.deleteUser(demoUser);
    cy.searchUser();
  })

});
