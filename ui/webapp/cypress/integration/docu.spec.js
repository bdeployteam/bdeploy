describe('Creates screenshots for the user documentation', () => {

  // Capture Login and Main menu
  it('Captures Login', () => {
    // Open app and wait for login page
    cy.visit('/').contains('Username');
    cy.screenshot('BDeploy_Login');

    // Login and wait for main page to be fully loaded
    cy.login();
    cy.visit('/');
    cy.contains('Instance Groups').should('exist');
    cy.waitUntilContentLoaded();

    // Capture empty overview page
    cy.waitUntilContentLoaded();
    cy.screenshot('BDeploy_Empty_IG');

    // Capture user menu
    cy.get('app-user-info').click();
    cy.contains('[role=menuitem]', 'admin').should('exist');
    cy.screenshot('BDeploy_User_Menu');
    cy.get('.cdk-overlay-backdrop').click('top', {force:true, multiple: true});

    // Open sidebar and wait for animation to be done
    cy.contains('button', 'menu').click();
    cy.get('mat-sidenav').should('have.css', 'transform', 'none');
    cy.screenshot('BDeploy_Main_Menu');
    cy.contains('a', 'Instance Groups').click();
    cy.get('mat-sidenav').should('have.css', 'visibility', 'hidden');
  });

  it('Captures Settings', () => {
    cy.login();

    cy.visit('/#/admin');
    cy.contains('.mat-tab-label', 'Look & Feel').should('be.visible');
    cy.waitUntilContentLoaded();

    cy.screenshot('BDeploy_General_Look_And_Feel');

    cy.visit('/#/admin/all/(panel:authentication)');

    cy.contains('.mat-tab-label', 'Local Users').should('be.visible');
    cy.waitUntilContentLoaded();

    cy.screenshot('BDeploy_Authentication_Local');

    cy.contains('.mat-tab-label', 'LDAP').click();
    cy.contains('button', 'add').click();

    cy.get('mat-dialog-container').within(() => {
      cy.get('[placeholder="Server URL"]').clear().type('ldaps://my-server');
      cy.get('[placeholder=Description]').type('My LDAP Server');
      cy.get('[placeholder="Server User"]').type('ldap.user@my-domain.com');
      cy.get('[placeholder="Account Base"]').type('dc=my-domain,dc=com').blur();
      cy.get('[placeholder="Server Password"]').type('xxx');

      cy.screenshot('BDeploy_LDAP_Server_Config', { padding: 20 });

      cy.contains('button', 'Apply').click();
    });

    cy.screenshot('BDeploy_LDAP_Servers');
  });

  // Captures Instance Group handling and Product upload
  it('Captures Instance Group Creation', () => {
    cy.login();
    cy.visit('/').waitUntilContentLoaded();

    // Create instance group
    cy.contains('button', 'add').click();
    cy.get('input[placeholder^="Instance group ID"]').type('Demo');
    cy.get('input[placeholder=Description]').type('Demo Instance Group');
    cy.get('input[type=file]').attachFile({ filePath: 'bdeploy.png', mimeType: 'image/png' });
    cy.get('.logo-img').should('exist');
    cy.screenshot('BDeploy_Create_IG');
    cy.contains('button', 'SAVE').click();

    // Newly created instance group
    cy.get('[data-cy=group-Demo]').should('exist');
    cy.waitUntilContentLoaded();
    cy.screenshot('BDeploy_Demo_IG');

    // Open instance page
    cy.contains('mat-card-title', 'Demo').click();
    cy.contains('Start by uploading');
    cy.screenshot('BDeploy_Empty_Instances');

    // Open products page
    cy.get('mat-toolbar').clickContextMenuAction('Products');
    cy.contains('Start by uploading');
    cy.screenshot('BDeploy_Empty_Products');

    // Upload products
    cy.contains('button', 'cloud_upload').click();
    cy.get('mat-dialog-container').within(() => {
      cy.get('input[type=file]').attachFile({
        filePath: 'test-product-1-direct.zip',
        mimeType: 'application/zip',
      });
      cy.get('input[type=file]').attachFile({
        filePath: 'test-product-2-direct.zip',
        mimeType: 'application/zip',
      });
      cy.screenshot('BDeploy_Product_Upload_Before');

      cy.contains('button', 'Upload').click();
      cy.get('td:contains("Upload successful")').should('have.length', 2);
      cy.screenshot('BDeploy_Product_Upload_Success');

      cy.contains('button', 'Close').click();
    });

    // Overview of products
    cy.contains('mat-card-title', 'Demo Product').should('be.visible');
    cy.screenshot('BDeploy_Products');

    // Products Versions
    cy.contains('mat-card-title', 'Demo Product').click();
    cy.get('mat-drawer').should('have.css', 'transform', 'none');
    cy.screenshot('BDeploy_Products_Details');
  });

  // Captures Instance creation
  it('Captures Instance Creation', () => {
    cy.login();
    cy.visit('/#/instance/browser/Demo').waitUntilContentLoaded();
    cy.contains('No instances have been defined yet.').should('be.visible');
    cy.contains('button', 'Add Instance').click();

    // Create new instance
    cy.get('[placeholder=Name]').type('Demo Instance');
    cy.get('[placeholder=Purpose]').click();
    cy.get('mat-option').contains('DEVELOPMENT').click();
    cy.get('[placeholder=Description]').type('Demo Instance');
    cy.get('[placeholder=Product]').click();
    cy.get('mat-option').contains('Demo Product').click();
    cy.get('[placeholder=Version]').click();
    cy.get('mat-option').contains('2.0.0').click();
    cy.get('mat-option').should('not.be.visible');
    cy.wait(250);
    cy.screenshot('BDeploy_Instance_Create');

    // Save instance
    cy.get('button').contains('SAVE').click();
    cy.waitUntilContentLoaded();
    cy.contains('mat-card-title', 'Demo Instance').should('be.visible');
    cy.screenshot('BDeploy_Instance_List');
  });

  // Captures Process Configuration
  it('Captures Process Configuration', () => {
    cy.login();
    cy.visit('/#/instance/browser/Demo').waitUntilContentLoaded();

    // Instance Overview page
    cy.get('mat-card-subtitle').first().click();
    cy.waitUntilContentLoaded();
    cy.get('app-instance-group-logo').parent().find('button').contains('more_vert').click();
    cy.wait(250);
    cy.screenshot('BDeploy_Instance_Menu');

    // Configure Applications
    cy.get('button').contains('Configure Applications').click();
    cy.getNodeCard('master').contains('Drop server application here').should('be.visible').then(el => {
      cy.contains('app-application-descriptor-card', 'Server Application').dragTo(el);
    })
    cy.getNodeCard('Client Applications').contains('Drop client application here').should('be.visible').then(el => {
      cy.contains('app-application-descriptor-card', 'Client Application').dragTo(el);
    })

    cy.getApplicationConfigCard('master', 'Server Application').contains('more_vert').click();
    cy.wait(250);
    cy.screenshot('BDeploy_DnD_Applications');

    cy.get('button').contains('Endpoints').click();
    cy.waitUntilContentLoaded();
    cy.screenshot('BDeploy_Endpoints_Config');
    cy.get('button').contains('CANCEL').click();
    cy.waitUntilContentLoaded();

    cy.getApplicationConfigCard('master', 'Server Application').contains('more_vert').click();
    cy.contains('button', 'Copy').click();
    cy.getNodeCard('master').contains('mat-card-header', 'master').contains('button', 'more_vert').click();
    cy.wait(250);
    cy.screenshot('BDeploy_Paste_Application');
    cy.contains('button', 'Paste').type('{esc}');

    cy.getApplicationConfigCard('master', 'Server Application').contains('more_vert').click();

    // Process Configuration
    cy.get('button').contains('Configure').click();
    cy.waitUntilContentLoaded();
    cy.screenshot('BDeploy_Process_Config');

    // Optional Parameters
    cy.contains('mat-expansion-panel', "Sleep Configuration").as('panel');
    cy.get('@panel').click();
    cy.get('@panel').contains('button', 'Manage Optional').click();
    cy.wait(250);
    cy.screenshot('BDeploy_Process_Optional_Parameters');

    cy.get('[placeholder=Filter').type("Sleep Timeout");
    cy.get('mat-dialog-container').contains('td', "Sleep Timeout").closest('tr').find('mat-checkbox').click();
    cy.get('mat-dialog-container').contains('button', 'Save').click();
    cy.get('[placeholder="Sleep Timeout"]').clear().type('10');
    cy.screenshot('BDeploy_Process_Optional_Configured');

    // Custom Parameters
    cy.contains('mat-expansion-panel', "Custom Parameters").as('panel');
    cy.get('@panel').click();
    cy.get('@panel').contains('button', 'Manage Custom').click();
    cy.get('button').contains('Create new parameter').click();
    cy.get('[placeholder=Predecessor]').click();
    cy.get('mat-option').contains('Sleep Timeout').click();
    cy.get('mat-option').should('not.be.visible');
    cy.screenshot('BDeploy_Process_Custom_Create');
    cy.get('mat-dialog-container').contains('button', 'Apply').click();
    cy.get('[placeholder=custom-param-1]').type("--customValue=Demo");
    cy.screenshot('BDeploy_Process_Custom_Value');

    // Command Line Preview
    cy.contains('button', 'input').click();
    cy.wait(250);
    cy.screenshot('BDeploy_Process_Custom_Preview');
    cy.get('.cdk-overlay-backdrop').click('top', {force:true, multiple: true});

    // Save and apply changes to go back to the overview
    cy.get('button').contains('APPLY').click();
    cy.get('button').contains('SAVE').click();
    cy.waitUntilContentLoaded();

    // Configuration files
    cy.get('app-instance-group-logo').parent().clickContextMenuAction('Configuration Files');
    cy.waitUntilContentLoaded();
    cy.screenshot('BDeploy_CfgFiles_Browser');

    // Configuration files editor
    cy.contains('button', 'add').click();
    cy.contains('New File').should('exist');
    cy.get('[placeholder="Enter path for file"]').type('test.json');
    cy.typeInAceEditor('{{}{enter}    "json": "is great"');

    cy.screenshot('BDeploy_CfgFile_New');
    cy.get('button').contains('APPLY').click();
    cy.waitUntilContentLoaded();

    // Configuration files added
    cy.screenshot('BDeploy_CfgFiles_Save');
    cy.get('button').contains('SAVE').click();
    cy.waitUntilContentLoaded();

    // Change product version (We first need to downgrade and save to get the desired state)
    cy.get('app-instance-group-logo').parent().clickContextMenuAction('Change Product Version');
    cy.contains('mat-slide-toggle','Show all').click();
    cy.contains('app-product-tag-card', '1.0.0').contains('button', 'arrow_downward').click();
    cy.contains('button', 'SAVE').should('be.enabled').click();
    cy.waitUntilContentLoaded();

    // Notification about newer product version
    cy.get('app-instance-notifications').click();
    cy.contains('button', 'Show Product Versions').should('exist');
    cy.screenshot('BDeploy_Product_Notification')
    cy.get('.cdk-overlay-backdrop').click('top', {force:true, multiple: true});

    cy.get('app-instance-group-logo').parent().clickContextMenuAction('Change Product Version');
    cy.screenshot('BDeploy_Product_Change')
    cy.contains('app-product-tag-card', '2.0.0').contains('button', 'arrow_upward').click();
    cy.screenshot('BDeploy_Product_Upgrade_Local_Changes');

    // Instance version menu
    cy.get('button').contains('SAVE').click();
    cy.waitUntilContentLoaded();
    cy.getLatestInstanceVersion().contains('more_vert').click();
    cy.wait(250);
    cy.screenshot('BDeploy_Instance_Version_Menu');

    // Install instance version
    cy.contains('button','Install').click();
    cy.getLatestInstanceVersion().waitUntilContentLoaded();
    cy.screenshot('BDeploy_Instance_Version_Installed');

    // Activate instance version
    cy.getLatestInstanceVersion().contains('more_vert').click();
    cy.contains('button','Activate').click();
    cy.getLatestInstanceVersion().waitUntilContentLoaded();
    cy.screenshot('BDeploy_Instance_Version_Activated');
  });

  // Captures Instance creation, process configuration, installation and activation
  it('Captures Process Control', () => {
    cy.login();
    cy.visit('/#/instance/browser/Demo').waitUntilContentLoaded();
    cy.get('mat-card-subtitle').first().click();

    // Start process
    cy.getApplicationConfigCard('master','Server Application').click();
    cy.contains('Process Control').should('exist');
    cy.contains('button','play_arrow').click();
    cy.wait(2000);
    cy.contains('app-process-status','favorite').should('be.visible');
    cy.screenshot('BDeploy_Process_Started');

    // Tutorial: Application card
    cy.getApplicationConfigCard('master','Server Application').screenshot('BDeploy_Tutorial_Process_Card', { padding: 20 })

    // Tutorial: Process control
    cy.contains('mat-toolbar','Process Control').parent().as('pcuSidebar');
    cy.get('@pcuSidebar').screenshot('BDeploy_Tutorial_Process_Control', { padding: 10, clip: { x: 0, y: 0, width: 390, height: 500  }})

    // Tutorial: Instance versions with process state
    cy.contains('mat-toolbar','Process Control').contains('button','close').click();
    cy.contains('mat-slide-toggle','Show all').click();
    cy.contains('mat-toolbar','Instance Versions').parent().as('versionSidebar');
    cy.get('@versionSidebar').contains('app-process-status','favorite').should('be.visible');
    cy.get('@versionSidebar').screenshot('BDeploy_Tutorial_Process_Versions', { padding: 10, clip: { x: 0, y: 0, width: 380, height: 500  }})
    cy.getApplicationConfigCard('master','Server Application').click();

    // Wait until the process crashes
    cy.contains('app-process-status','report_problem', { timeout: 60000 });
    cy.screenshot('BDeploy_Process_Crashed');

    // Wait until the process crashes permanently
    cy.contains('app-process-status','error', { timeout: 60000 });
    cy.screenshot('BDeploy_Process_Crashed_Repeatedly');

    // Switch to another version to capture Outdated process
    cy.contains('button','play_arrow').click();
    cy.contains('mat-toolbar','Process Control').contains('button','close').click();
    cy.contains('mat-slide-toggle','Show all').click();
    cy.get('app-instance-version-card').children().eq(1).installAndActivate();
    cy.waitUntilContentLoaded();
    cy.contains("Outdated").should('be.visible');
    cy.screenshot('BDeploy_Process_Out_Of_Sync', { clip: { x: 0, y: 175, width: 650, height: 380 }});
    cy.getApplicationConfigCard('master','Server Application').click();
    cy.contains('Process Control').should('exist');
    cy.contains('button','stop').should('be.enabled').click();
    cy.contains('mat-toolbar','Process Control').contains('button','close').click();
    cy.get('app-instance-version-card').first().contains('Version').click();
    cy.getApplicationConfigCard('master','Server Application').click();

    // Change to manual confirmation
    cy.contains('mat-toolbar','Process Control').contains('button','close').click();
    cy.getApplicationConfigCard('master','Server Application').clickContextMenuAction("Configure");
    cy.get('mat-select').contains("MANUAL").click();
    cy.get('mat-option').contains('MANUAL_CONFIRM').click();

    // Set Output parameter so that we have a file in the data-files folder
    cy.addAndSetOptionalParameter('Test Parameters', 'Output', '{{P:DATA}}/documentation.txt');

    // Apply, Save and activate the changes
    cy.contains('button','APPLY').click();
    cy.contains('button','SAVE').click();
    cy.waitUntilContentLoaded();
    cy.getLatestInstanceVersion().installAndActivate();

    // try to downgrade, capture validation problems, discard
    cy.get('app-instance-group-logo').parent().clickContextMenuAction('Change Product Version');
    cy.contains('mat-slide-toggle','Show all').click();
    cy.contains('app-product-tag-card', '1.0.0').contains('button', 'arrow_downward').click();
    cy.contains('app-instance-notifications', 'error').click();
    cy.screenshot('BDeploy_Product_Downgrade_Validation')
    cy.get('.cdk-overlay-backdrop').click('top', {force:true, multiple: true});
    cy.contains('button', 'DISCARD').should('be.enabled').click();
    cy.get('mat-dialog-container').within(_ => {
      cy.contains('button', 'Yes').should('be.enabled').click();
    });

    cy.get('body').then($body => {
      if($body.find('button:contains("DISCARD")').length > 0) {
        cy.contains('button', 'DISCARD').should('not.exist');
      }
    })

    // Start process with manual confirmation
    cy.getApplicationConfigCard('master','Server Application').click();
    cy.contains('Process Control').should('exist');
    cy.contains('button','play_arrow').click();
    cy.wait(250);
    cy.screenshot('BDeploy_Process_Manual_Confirm');
    cy.get('app-process-start-confirm').get('input').type('Server Application');
    cy.contains('button','Start').click();

    // Process Output
    cy.wait(2000); // this is required here to give the process time to start. otherwise the out.txt does not yet exist
    cy.contains('button','message').click();
    cy.contains('Output of').should('be.visible');
    cy.wait(1000); // some time for the terminal to paint the output
    cy.screenshot('BDeploy_Process_Output');
    cy.get('.cdk-overlay-backdrop').click('top', {force:true, multiple: true});

    // Process Listing
    cy.get('button').contains('settings').click();
    cy.wait(250);
    cy.screenshot('BDeploy_Process_List');
    cy.get('.cdk-overlay-backdrop').click('top', {force:true, multiple: true});

    // Data Files
    cy.get('app-instance-group-logo').parent().clickContextMenuAction('Data Files');
    cy.contains('td', 'documentation.txt').should('exist')
    cy.screenshot('BDeploy_DataFiles_Browser');

    // Data File Content
    cy.contains('td', 'documentation.txt').click();
    cy.contains('mat-toolbar', 'documentation.txt').should('be.visible');
    cy.wait(1000); // some time for the terminal to paint the output
    cy.screenshot('BDeploy_DataFiles_Show');
    cy.get('.cdk-overlay-backdrop').click('top', {force:true, multiple: true});
  });

  // Capture Client Applications
  it('Captures Client Applications', () => {
    cy.login();
    cy.visit('/#/instancegroup/clientapps/Demo');

    cy.contains('Instance Group: Demo').should('exist');
    cy.waitUntilContentLoaded();
    cy.wait(250);
    cy.screenshot('BDeploy_Client_Download_Page');
  });

  // Capture Software Repositories
  it('Captures Software Repositories', () => {
    cy.login();
    cy.visit('/#/softwarerepo/browser');

    // Add new repository
    cy.contains('Software Repositories').should('exist');
    cy.contains('button', 'add').click();
    cy.contains('button', 'SAVE').should('exist').and('be.disabled');
    cy.get('input[placeholder^="Software Repository name"]').click();
    cy.get('input[placeholder^="Software Repository name"]').type('External');
    cy.get('input[placeholder=Description]').type('External Software Repository');
    cy.get('button').contains('SAVE').click();
    cy.waitUntilContentLoaded();
    cy.screenshot('BDeploy_SWRepos');

    // Software repository page
    cy.contains('mat-card-title', 'External').click();
    cy.waitUntilContentLoaded();

    // Upload external software
    cy.contains('button', 'cloud_upload').click();
    cy.get('mat-dialog-container').within(() => {
      cy.get('input[type=file]').attachFile({
        filePath: 'external-software-hive.zip',
        mimeType: 'application/zip',
      });
      cy.contains('button', 'Upload').click();
      cy.get('td:contains("Upload successful")').should('have.length', 1);
      cy.contains('button', 'Close').click();
    });

    // Take screenshot of detail page
    cy.waitUntilContentLoaded();
    cy.get('mat-card-title').should('have.length.gte', 2);
    cy.screenshot('BDeploy_SWRepo_Ext_Software');

    // Capture detail of external software
    cy.contains('mat-card-title','external/software/windows').click();
    cy.get('mat-drawer').should('have.css', 'transform', 'none');
    cy.screenshot('BDeploy_SWRepo_Ext_Software_Details');
  });

  // Capture BDeploy Update
  it('Captures BDeploy Update', () => {
    cy.login();
    cy.visit('/#/admin/all/(panel:systemsoftware)');

    // Available software
    cy.contains('BDeploy Update').should('exist');
    cy.get('mat-card-content').should('be.visible').should('have.length', 2)
    cy.waitUntilContentLoaded();
    cy.screenshot('BDeploy_System_With_Launcher');
  });

  // Capture Manual Cleanup
  it('Captures Manual Cleanup', () => {
    cy.login();
    cy.visit('/#/admin/all/(panel:manualcleanup)');

    // Introduction
    cy.contains('button', 'Calculate Cleanup Action').should('be.visible')
    cy.screenshot('BDeploy_Cleanup');

    // Perform Actions
    cy.contains('button', 'Calculate Cleanup Action').click();
    cy.waitUntilContentLoaded();
    cy.contains('Perform Cleanup on Instance Group').should('be.visible')
    cy.screenshot('BDeploy_Cleanup_Actions');
  });

  it('Cleans Instance Group', function() {
    cy.login();
    cy.visit('/');
    cy.waitUntilContentLoaded();
    cy.deleteInstanceGroup('Demo');
  });

  it('Cleans Software Repository', function() {
    cy.login();
    cy.visit('/#/softwarerepo/browser');
    cy.waitUntilContentLoaded();
    cy.contains('app-software-repository-card', 'External').clickContextMenuDialog('Delete', 'Delete Software Repository');
    cy.contains('button', 'OK').click();
  });

});
