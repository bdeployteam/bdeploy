import { BackendApi } from '@bdeploy-backend';
import { expect, test } from '@bdeploy-setup';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { TestInfo } from '@playwright/test';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';

function groupId(testInfo: TestInfo) {
  return `S005Group-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for S005`);
});

test.afterEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId(testInfo));
});

/**
 * This is setup to have some variance in the input from each template.
 */
test('S005 Apply instance template', async ({ standalone }, testInfo) => {
  /*--- Chat Product  ---*/
  await uploadProduct(standalone, groupId(testInfo), 'chat-product-1-direct');
  await createInstance(standalone, groupId(testInfo), 'Chat Instance', 'S005 test', InstancePurpose.PRODUCTIVE, 'Demo Chat App', '1.0.0');
  const chatInstanceDashboard = new InstanceDashboardPage(standalone, groupId(testInfo), 'Chat Instance');
  await chatInstanceDashboard.goto();

  const chatInstanceConfig = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Chat Instance');
  await chatInstanceConfig.goto();

  const chatInstanceSettings = await chatInstanceConfig.getSettingsPanel();
  const chatInstanceTemplate = await chatInstanceSettings.getInstanceTemplatesPanel();
  await chatInstanceTemplate.selectTemplate('Default Configuration');
  await chatInstanceTemplate.selectGroup('Chat App', 'master');
  await chatInstanceTemplate.finishGroupSelection();

  await chatInstanceTemplate.fillLiteralVariable('AppName', 'Chat5');
  await chatInstanceTemplate.finishTemplate();

  // check correct number of processes
  await chatInstanceConfig.shouldHaveNodeCount(1);
  await chatInstanceConfig.shouldHaveControlGroupCountForNode('master', 1);
  await chatInstanceConfig.shouldHaveProcessCountForNode('master', 1);
  await expect(chatInstanceConfig.getProcessRow('master', 'The Chat App Chat5')).toBeVisible();

  // check base configuration is correct
  const chatInstanceBaseConfig = await chatInstanceSettings.getBaseConfigurationPanel();
  // this is not specified in the template, but checking it anyway
  await chatInstanceBaseConfig.getAutomaticStartup().shouldNotBeChecked();
  await chatInstanceBaseConfig.getAutomaticUninstall().shouldBeChecked();
  await chatInstanceConfig.save();

  /*--- Product 2  ---*/
  await uploadProduct(standalone, groupId(testInfo), 'test-product-2-direct');
  await createInstance(standalone, groupId(testInfo), 'Product 2 Instance', 'S005 test', InstancePurpose.TEST, 'Demo Product', '2.0.0');
  const secondInstanceDashboard = new InstanceDashboardPage(standalone, groupId(testInfo), 'Product 2 Instance');
  await secondInstanceDashboard.goto();

  const secondInstanceConfig = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Product 2 Instance');
  await secondInstanceConfig.goto();

  const secondInstanceSettings = await  secondInstanceConfig.getSettingsPanel();
  const secondInstanceTemplate = await secondInstanceSettings.getInstanceTemplatesPanel();
  await secondInstanceTemplate.selectTemplate('Default Configuration');
  await secondInstanceTemplate.selectGroup('Server Apps', 'master');
  await secondInstanceTemplate.selectGroup('Client Apps', 'Client Applications');
  await secondInstanceTemplate.finishGroupSelection();

  await secondInstanceTemplate.fillLiteralVariable('Text Value', 'Demo Text');
  await secondInstanceTemplate.fillLiteralVariable('Sleep Timeout', '3');
  await secondInstanceTemplate.fillBooleanVariable('Product License', true);
  await secondInstanceTemplate.finishTemplate();

  // check correct number of processes
  await secondInstanceConfig.shouldHaveNodeCount(2);
  await secondInstanceConfig.shouldHaveProcessCountForNode('__ClientApplications', 3);
  await secondInstanceConfig.shouldHaveControlGroupCountForNode('master', 2);
  await secondInstanceConfig.shouldHaveProcessCountForNode('master', 3);

  // check base configuration is correct
  const secondInstanceBaseConfig = await secondInstanceSettings.getBaseConfigurationPanel();
  // autoStart = true and autoInstall = false in template IS IGNORED
  await secondInstanceBaseConfig.getAutomaticStartup().shouldNotBeChecked();
  await secondInstanceBaseConfig.getAutomaticUninstall().shouldBeChecked();
});

