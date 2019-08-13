describe('Instance Tests', function () {
  beforeEach(function () {
    cy.login();
  })

  it('Create a new instance', function () {
    cy.createInstance('CreateInstanceTest')
  })
})
