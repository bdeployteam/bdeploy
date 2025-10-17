import { BackendApi } from '@bdeploy-backend';
import { expect, test } from '@bdeploy-setup';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { TestInfo } from '@playwright/test';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';
import { InstanceDataFilesPage } from '@bdeploy-pom/primary/instances/instance-data-files.page';
import { ClientAppsPage } from '@bdeploy-pom/primary/groups/client-apps.page';
import { InstanceLogFilesPage } from '@bdeploy-pom/primary/instances/instance-log-files.page';

test.slow();

function groupId(testInfo: TestInfo) {
  return `InstGroup-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId(testInfo));
  await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for instance tests`);
});

test.afterEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId(testInfo));
});

test('Instance Dashboard', async ({ standalone }, testInfo) => {
  await uploadProduct(standalone, groupId(testInfo), 'test-product-2-direct');
  await createInstance(standalone, groupId(testInfo), 'Empty Instance', 'Empty Instance for documentation', InstancePurpose.TEST, 'Demo Product', '2.0.0');

  const instance = new InstanceDashboardPage(standalone, groupId(testInfo), 'Empty Instance');
  await instance.goto();

  await instance.screenshot('Doc_InstanceEmpty');

  const config = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Empty Instance');
  await config.goto();

  const settings = await config.getSettingsPanel();
  const templates = await settings.getInstanceTemplatesPanel();
  await templates.screenshot('Doc_InstanceTemplates');

  await templates.selectTemplate('Default Configuration');
  await templates.selectGroup('Server Apps', 'master');
  await templates.selectGroup('Client Apps', 'Client Applications');
  await templates.screenshot('Doc_InstanceTemplatesNodes');
  await templates.finishGroupSelection();

  await templates.screenshot('Doc_InstanceTemplatesVars');
  await templates.fillLiteralVariable('Text Value', 'Demo Text');
  await templates.fillLiteralVariable('Sleep Timeout', '1');
  await templates.fillBooleanVariable('Product License', true);
  await templates.finishTemplate();

  await expect(templates.getDialog()).not.toBeAttached();
  await expect(config.getConfigNode('master').locator('tr', { hasText: 'Server No Sleep' })).toBeVisible();
  await config.waitForValidation();
  await config.screenshot('Doc_InstanceTemplatesDone');

  const editPanel = await config.getProcessControlGroupArea('master', 'First Group').getEditPanel();
  await editPanel.screenshot('Doc_InstanceConfigEditProcessControlGroup');

  await config.save();

  await standalone.waitForURL(`/#/instances/dashboard/${groupId(testInfo)}/**`);
  await instance.expectOpen();
  await instance.install('2');
  await instance.activate();

  await expect(instance.getServerNode('master').locator('tr', { hasText: 'Server No Sleep' })).toBeVisible();
  await instance.screenshot('Doc_InstanceDashboardActive');
  await instance.screenshot('Doc_DemoInstance'); // TODO: duplicate screenshots in docu with different names.

  await instance.getProcessStatus('master', 'Server With Sleep');
  await instance.screenshot('Doc_DashboardProcessControl');

  await instance.toggleBulkControl();
  await instance.getServerNode('master').locator('tr', { hasText: 'Second Group' }).getByRole('checkbox').click();
  await instance.screenshot('Doc_DashboardBulkProcessControl');

  const manualStatus = await instance.getProcessStatus('master', 'Server No Sleep');
  await manualStatus.start();
  await manualStatus.screenshot('Doc_DashboardProcessManualConfirm');
  await manualStatus.getConfirmationPopup().cancel();

  let keepAliveStatus = await instance.getProcessStatus('master', 'Another Server');
  await keepAliveStatus.start();

  // actually need to wait for the process to fail - timeout is set to 1 second during template setup.
  // we wait 2 seconds; 1 seconds for the first "crash" to happen (then it restarts immediately, we could
  // do a screenshot of the "red heart" (running recently crashed) as well, but it is not currently in the
  // documentation. We wait a little longer on all waits to accommodate for any delays in the backend.
  await standalone.waitForTimeout(1000 * 1.2); // first run
  await standalone.waitForTimeout(1000 * 1.2); // second run
  await instance.getServerNode('master').getByTestId('refresh-processes').click();
  await expect(instance.getServerNode('master').locator('tr', { hasText: 'Another Server' }).locator('mat-icon', { hasText: 'report_problem' })).toBeVisible();
  await instance.screenshot('Doc_DashboardProcessCrash');

  // now there is a restart-back-off of 10 seconds on the server, we also need to wait for that.
  await standalone.waitForTimeout(10500); // backoff

  // now the process is running again. we set retries for this process to 2, so it should enter failed
  // permanently state after another 3 seconds.
  await standalone.waitForTimeout(1000 * 1.2); // third run
  await instance.getServerNode('master').getByTestId('refresh-processes').click();
  await expect(instance.getServerNode('master').locator('tr', { hasText: 'Another Server' }).locator('mat-icon', { hasText: 'error' })).toBeVisible();
  await instance.screenshot('Doc_DashboardProcessCrashPermanent');

  const outputPanel = await keepAliveStatus.getProcessConsole();

  // TODO: better way to wait for output? it is drawn in a canvas, so we *could* wait for the API request to return
  //       but I don't think there is a much better way without disabling hardware acceleration.
  await standalone.waitForTimeout(500);
  await outputPanel.screenshot('Doc_DashboardProcessConsole');

  // pin a parameter, install & activate to screenshot the process details panel.
  await config.goto();
  const anotherServerSettings = await config.getProcessSettingsPanel('master', 'Another Server');
  const anotherServerCfg = await anotherServerSettings.getConfigureParametersPanel();
  const sleepCfgGroup = await anotherServerCfg.getParameterGroup('Sleep Configuration');
  await sleepCfgGroup.toggle();
  const param = sleepCfgGroup.getParameter('param.sleep');
  await param.hover();
  await param.locator('mat-icon', { hasText: 'pin' }).click();
  await standalone.mouse.move(0, 0);
  await anotherServerCfg.screenshot('Doc_InstanceConfigParameterPin');
  await anotherServerCfg.apply();
  await config.waitForValidation();
  await config.save();

  await instance.expectOpen();
  await instance.install('3');
  await instance.activate();

  keepAliveStatus = await instance.getProcessStatus('master', 'Another Server');
  await keepAliveStatus.screenshot('Doc_DashboardPinnedParameter');

  // last use this test to screenshot the client apps page
  const clients = new ClientAppsPage(standalone, groupId(testInfo));
  await clients.goto();
  await clients.screenshot('Doc_ClientApps');
});

test('Instance Configuration', async ({ standalone }, testInfo) => {
  await uploadProduct(standalone, groupId(testInfo), 'test-product-2-direct');
  await createInstance(standalone, groupId(testInfo), 'Configure Instance', 'Instance for configuration documentation', InstancePurpose.TEST, 'Demo Product', '2.0.0');

  const config = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Configure Instance');
  await config.goto();

  const settings = await config.getSettingsPanel();

  const nodes = await settings.getManageNodesPanel();
  await nodes.screenshot('Doc_InstanceManageNodes');
  await nodes.getBackToOverviewButton().click();

  const addPcgPanel = await config.getAddProcessControlGroupPanel('master');
  await addPcgPanel.screenshot('Doc_InstanceConfigAddProcessControlGroup');

  let process = await config.getAddProcessPanel('master');
  await process.screenshot('Doc_InstanceAddProcessPanel');
  await process.addProcess('Server Application');
  await config.waitForValidation();

  await expect(config.getConfigNode('master').getByRole('row', { name: 'Server Application' })).toBeVisible();
  await config.screenshot('Doc_InstanceNewProcess');

  await process.addProcess('Server Application');
  await config.waitForValidation();
  await expect(config.getConfigNode('master').getByRole('row', { name: 'Server Application' })).toHaveCount(2);
  await config.screenshot('Doc_InstanceConfigValidation');

  const changes = await config.getLocalChangesPanel();
  await config.undo();
  await config.waitForValidation();

  await config.screenshot('Doc_InstanceConfigLocalChanges');

  const compare = await changes.getCompareWithBasePanel();
  await compare.screenshot('Doc_InstanceConfigCompareChanges');
  await compare.getBackToOverviewButton().click();

  const processSettings = await config.getProcessSettingsPanel('master', 'Server Application');
  await processSettings.screenshot('Doc_InstanceConfigProcessSettings');
  await processSettings.screenshot('Doc_InstanceConfig_Endpoints'); // TODO: rework documentation to use the screenshot above or rework the screenshot to not be the same.

  const eps = await processSettings.getConfigureEndpointsPanel();
  await eps.screenshot('Doc_InstanceConfig_EndpointsConfig');
  await eps.getBackToOverviewButton().click();

  const paramPanel = await processSettings.getConfigureParametersPanel();
  await paramPanel.screenshot('Doc_InstanceConfigParams');

  const sleepConfig = await paramPanel.getParameterGroup('Sleep Configuration');
  await sleepConfig.toggle();
  await sleepConfig.selectParameters();

  await sleepConfig.screenshot('Doc_InstanceConfigOptionalParams');

  await sleepConfig.getParameter('param.sleep').locator('mat-icon', { hasText: 'add' }).click();
  await expect(sleepConfig.getParameter('param.sleep').locator('mat-icon', { hasText: 'delete' })).toBeVisible();
  await sleepConfig.finishSelectParameters();

  const customParam = await paramPanel.getParameterGroup('Custom Parameters');
  await customParam.toggle();
  const customPopup = await customParam.addCustomParameter();
  await customPopup.fill('custom.param', '--text=Custom', 'Sleep Timeout');

  await customPopup.screenshot('Doc_InstanceConfigAddCustomParam');
  await customPopup.cancel();

  const testParams = await paramPanel.getParameterGroup('Test Parameters');
  await sleepConfig.toggle();
  await testParams.toggle();
  await testParams.selectParameters();
  await testParams.getParameter('param.text').locator('mat-icon', { hasText: 'add' }).click();
  await testParams.finishSelectParameters();

  // scroll down a little so we can get the content assist below the input.
  await customParam.scrollIntoView();

  // click the "link expression" toggle
  await testParams.getParameter('param.text').getByRole('radio').nth(1).click();
  const paramText = testParams.getParameter('param.text').locator('id=param.text_link');
  await paramText.focus();
  await paramText.pressSequentially('{{A:');
  await testParams.screenshot('Doc_InstVar_InParameter');
  await paramText.pressSequentially('UUID}}');

  await paramPanel.showCommandPreview();
  await paramPanel.screenshot('Doc_InstanceConfigPreview');

  await paramPanel.getBackToOverviewButton().click();
  await paramPanel.getSavePopup().getByRole('button', { name: 'Discard' }).click();

  // TODO: reload to get rid of possible remaining artifacts - why necessary?
  //       seems like a parameter tooltip is reproducibly stuck in the test.
  await standalone.reload();

  // show application template
  process = await config.getAddProcessPanel('master');
  await process.addProcessTemplate('Server With Sleep');
  // that opened the variable input dialog.
  const varDlg = process.getVariableTemplatePopup();
  await varDlg.fillTextVariable('Sleep Timeout', '40');
  await varDlg.screenshot('Doc_InstanceAddProcessTemplVars');
  await varDlg.cancel();

  // show client application config file filter
  const addClientProcess = await config.getAddProcessPanel('Client Applications');
  await addClientProcess.addProcess('Client Application');
  await config.waitForValidation();

  await expect(config.getConfigNode('Client Applications').getByRole('row', { name: 'Client Application' })).toHaveCount(3);
  const clientSettings = await config.getProcessSettingsPanel('Client Applications', 'Client Application');
  const clientParams = await clientSettings.getConfigureParametersPanel();

  await clientParams.getAllowedConfigDirPaths().locator('input').focus();
  await clientParams.getAllowedConfigDirPaths().locator('mat-icon', { hasText: /file/ }).click();
  await clientParams.screenshot('Doc_InstanceConfig_ClientConfigDirs');
});

test('Instance Variables', async ({ standalone }, testInfo) => {
  await uploadProduct(standalone, groupId(testInfo), 'test-product-2-direct');
  await createInstance(standalone, groupId(testInfo), 'Variable Instance', 'Instance for variable documentation', InstancePurpose.TEST, 'Demo Product', '2.0.0');

  const config = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Variable Instance');
  await config.goto();

  const settings = await config.getSettingsPanel();
  const varPanel = await settings.getInstanceVariablePanel();
  const customVarGroup = await varPanel.getVariableGroup('Custom Variables');
  await customVarGroup.toggle();
  const customVarDialog = await customVarGroup.addCustomVariable();
  await customVarDialog.fill('custom.var', '4711', 'This is a custom numeric variable', 'NUMERIC');

  await varPanel.screenshot('Doc_InstVar_Plain');

  await customVarDialog.fillLink('{{A:UUID}}');

  await varPanel.screenshot('Doc_InstVar_Link');
});

test('Instance Configuration Files', async ({ standalone }, testInfo) => {
  await uploadProduct(standalone, groupId(testInfo), 'test-product-2-direct');
  await createInstance(standalone, groupId(testInfo), 'Config File Instance', 'Instance for configuration file documentation', InstancePurpose.TEST, 'Demo Product', '2.0.0');

  const config = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Config File Instance');
  await config.goto();

  const settings = await config.getSettingsPanel();
  const configFiles = await settings.getConfigurationFilesPanel();
  await configFiles.screenshot('Doc_InstanceConfigFiles');

  await configFiles.addFile('test.json');
  const editor = await configFiles.editFile('test.json');
  await editor.fill('{\n    "json": "content"');

  await editor.screenshot('Doc_InstanceConfigFilesEdit');
});

test('Instance Product Version', async ({ standalone }, testInfo) => {
  await uploadProduct(standalone, groupId(testInfo), 'test-product-1-direct');
  await uploadProduct(standalone, groupId(testInfo), 'test-product-2-direct');
  await createInstance(standalone, groupId(testInfo), 'Product Version Instance', 'Instance for product version documentation', InstancePurpose.TEST, 'Demo Product', '1.0.0');

  const config = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Product Version Instance');
  await config.goto();

  const process = await config.getAddProcessPanel('master');
  await process.addProcess('Server Application');

  await config.save();
  await config.goto(); // get back xD

  await config.screenshot('Doc_InstanceProductUpdateAvail');

  const settings = await config.getSettingsPanel();
  const versions = await settings.getProductVersionPanel();

  await config.screenshot('Doc_InstanceProductUpdate');
  await versions.setVersion('2.0.0');

  // easiest way to close panel without navigating back.
  await config.getToolbar().getByRole('button', { name: 'Instance Settings' }).click();

  await config.waitForValidation();
  await config.screenshot('Doc_InstanceProductUpdateHints');
});

test('Instance Banner', async ({ standalone }, testInfo) => {
  await uploadProduct(standalone, groupId(testInfo), 'test-product-2-direct');
  await createInstance(standalone, groupId(testInfo), 'Banner Instance', 'Instance for banner documentation', InstancePurpose.TEST, 'Demo Product', '2.0.0');

  const config = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Banner Instance');
  await config.goto();

  const settings = await config.getSettingsPanel();
  const banner = await settings.getBannerPanel();
  await banner.fill('This is a banner text', 'Positive');

  await banner.screenshot('Doc_InstanceBannerConfig');
  await banner.apply();

  // need to wait for changes to be visible...
  await expect(config.getBanner()).toBeVisible();
  await expect(config.getBanner().getByText('This is a banner text')).toBeVisible();

  await config.screenshot('Doc_InstanceBanner');
});

test('Instance Data Files', async ({ standalone }, testInfo) => {
  await uploadProduct(standalone, groupId(testInfo), 'test-product-2-direct');
  await createInstance(standalone, groupId(testInfo), 'Data Files Instance', 'Instance for data files documentation', InstancePurpose.TEST, 'Demo Product', '2.0.0');

  const config = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Data Files Instance');
  await config.goto();
  const addPanel = await config.getAddProcessPanel('master');
  await addPanel.addProcess('Server Application');

  await config.waitForValidation();
  await config.save();

  const dashboard = new InstanceDashboardPage(standalone, groupId(testInfo), 'Data Files Instance');
  await dashboard.install('2');
  await dashboard.activate();

  // we have an empty instance active, so we can now use the data files browser.
  const df = new InstanceDataFilesPage(standalone, groupId(testInfo), 'Data Files Instance');
  await df.goto();

  const addDf = await df.getAddFilePanel();
  await addDf.fill('application.log', 'application-test.log');
  await addDf.save();

  await expect(df.getTableRowContaining('application.log')).toBeVisible();
  await df.screenshot('Doc_DataFiles');
  const dfView = await df.getFileViewer('application.log');
  // TODO: wait for terminal to initialize... there is a TODO for this already further up.
  await standalone.waitForTimeout(500);

  await dfView.screenshot('Doc_DataFilesView');
  await dfView.getCloseButton().click();

  await df.getAddFilePanel();
  await addDf.fill('manual.txt');
  await addDf.save();

  await df.getFileViewer('manual.txt');
  const dfEditor = await dfView.getFileEditorPanel();
  await dfEditor.fill('This is a sample text file.\n');
  await dfEditor.screenshot('Doc_DataFilesEdit');
});

test('Instance Log Files', async ({ standalone }, testInfo) => {
  await uploadProduct(standalone, groupId(testInfo), 'test-product-2-direct');
  await createInstance(standalone, groupId(testInfo), 'Log Files Instance', 'Instance for log files documentation', InstancePurpose.TEST, 'Demo Product', '2.0.0');

  const config = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Log Files Instance');
  await config.goto();
  const addPanel = await config.getAddProcessPanel('master');
  await addPanel.addProcess('Server Application');
  const settings = await config.getProcessSettingsPanel('master', 'Server Application');
  const params = await settings.getConfigureParametersPanel();
  const testParams = await params.getParameterGroup('Test Parameters');
  await testParams.toggle();
  await testParams.selectParameters();
  await testParams.getParameter('param.out').locator('mat-icon', { hasText: 'add' }).click();
  await testParams.finishSelectParameters();
  await testParams.getParameter('param.out').locator('id=param.out_link').fill('{{P:LOG_DATA}}/out.log');
  await params.apply();

  await config.waitForValidation();
  await config.save();

  const dashboard = new InstanceDashboardPage(standalone, groupId(testInfo), 'Log Files Instance');
  await dashboard.install('2');
  await dashboard.activate();

  const status = await dashboard.getProcessStatus('master', 'Server Application');
  await status.start();

  const logs = new InstanceLogFilesPage(standalone, groupId(testInfo), 'Log Files Instance');
  await logs.goto();
  await expect(logs.getTableRowContaining('out.log')).toBeVisible();

  await logs.screenshot('Doc_LogFiles');
  const viewer = await logs.getFileViewer('out.log');
  await viewer.screenshot('Doc_LogFilesView');
});
