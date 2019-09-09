describe('Instance Tests', function () {
  var instanceUuid;

  before(function () {
    cy.login();
  })

  /**
   * Creates a new instance and sets the instanceUuid variable to the resulting UUID
   */
  it('Create a new instance', function () {
    cy.createInstance('CreateInstanceTest').then(uuid => {
      instanceUuid = uuid;

      cy.get('body').contains(instanceUuid).should('exist');
    })

  })

  /**
   * Delete the instance with the well-known UUID
   */
  it('Delete the instance', function () {
    // open the menu on the card
    cy.get('mat-card-subtitle').contains(instanceUuid).parent().siblings('button').contains('more_vert').click();

    // find the delete button
    cy.get('button').get('[role=menuitem]').contains('Delete').click();

    // place a trigger on the endpoint, so we can later wait for it
    cy.server()
    cy.route('GET', '/api/group/Test/instance').as('reload')

    // in the resulting dialog, click OK
    cy.get('mat-dialog-container').get('button').contains('OK').click();

    // wait for the dialog to disappear and the page to reload
    cy.wait('@reload')
    cy.get('mat-progress-spinner').should('not.exist')

    // now NO trace of the UUID should be left.
    cy.get('body').contains(instanceUuid).should('not.exist');
  })
})
