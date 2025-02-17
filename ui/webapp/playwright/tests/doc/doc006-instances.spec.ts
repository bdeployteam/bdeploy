import { BackendApi } from '@bdeploy-backend';
import { expect, test } from '@bdeploy-setup';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { TestInfo } from '@playwright/test';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';

test.slow();

function groupId(testInfo: TestInfo) {
  return `InstGroup-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
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

  await templates.selectTemplate('Default Configuration');
  await templates.selectGroup('Server Apps', 'master');
  await templates.selectGroup('Client Apps', 'Client Applications');
  await templates.finishGroupSelection();

  await templates.fillLiteralVariable('Text Value', 'Demo Text');
  await templates.fillBooleanVariable('Product License', true);
  await templates.finishTemplate();

  await expect(templates.getDialog()).not.toBeAttached();
  await expect(config.getConfigNode('master').locator('tr', { hasText: 'Server No Sleep' })).toBeVisible();

  await config.save();

  await standalone.waitForURL(`/#/instances/dashboard/${groupId(testInfo)}/**`);
  await instance.expectOpen();

  // TODO: if we click the install button too fast, strange things happen:
  //        * The install button remains active.
  //        * a 'state' request is fired and the server reports instance version 1 as installed.
  //        * this remains, even when the page is reloaded (?!?!)
  //       I thought this was due to not using observable/signal, but this seems not to be
  //       the root cause. Also the install button seems to "flicker" once in real world as
  //       well when loading the page (sometimes).
  await standalone.waitForTimeout(200);

  await instance.install();
  await instance.activate();

  await expect(instance.getServerNode('master').locator('tr', { hasText: 'Server No Sleep' })).toBeVisible();
  await instance.screenshot('Doc_InstanceDashboardActive');
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

  const process = await config.getAddProcessPanel('master');
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

  const addClientProcess = await config.getAddProcessPanel('__ClientApplications');
  await addClientProcess.addProcess('Client Application');
  await config.waitForValidation();

  await expect(config.getConfigNode('__ClientApplications').getByRole('row', { name: 'Client Application' })).toHaveCount(2);
  const clientSettings = await config.getProcessSettingsPanel('__ClientApplications', 'Client Application');
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
