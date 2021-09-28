//@ts-check

describe('Groups Tests', () => {
  var groupName = 'Demo';

  before(() => {
    cy.cleanAllGroups();
  });

  beforeEach(() => {
    cy.login();
  });

  it('Creates a group', () => {
    cy.visit('/');
    cy.createGroup(groupName);
  });

  // TODO client applications page

  it('Deletes the group', () => {
    cy.deleteGroup(groupName);
  });
});
