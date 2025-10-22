import { BackendApi } from '@bdeploy-backend';
import { expect, test } from '@bdeploy-setup';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { TestInfo } from '@playwright/test';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';

function groupId(testInfo: TestInfo) {
  return `S012Group-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for S012`);
});

test.afterEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId(testInfo));
});

test('S012 Running process removal from instance', async ({ standalone }, testInfo) => {
  await uploadProduct(standalone, groupId(testInfo), 'chat-product-1-direct');
  await createInstance(standalone, groupId(testInfo), 'Chat Instance', 'Running process removal from instance', InstancePurpose.TEST, 'Demo Chat App', '1.0.0');

  const chatInstanceDashboard = new InstanceDashboardPage(standalone, groupId(testInfo), 'Chat Instance');
  await chatInstanceDashboard.goto();

  // create instance with one process
  const instanceConfig = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Chat Instance');
  await instanceConfig.goto();

  const addProcessPanel = await instanceConfig.getAddProcessPanel('master');
  await addProcessPanel.addProcessTemplate('App with Https');
  await instanceConfig.save();

  await standalone.waitForURL(`/#/instances/dashboard/${groupId(testInfo)}/**`);
  await chatInstanceDashboard.expectOpen();
  await chatInstanceDashboard.install('2');
  await chatInstanceDashboard.activate();

  await chatInstanceDashboard.shouldHaveServerNodeCount(1);
  await chatInstanceDashboard.shouldHaveProcessCountForNode('master', 1);

  // start and wait to start
  await expect(chatInstanceDashboard.getServerNode('master').locator('tr', { hasText: 'App with Https' })).toBeVisible();
  const manualStatus = await chatInstanceDashboard.getProcessStatus('master', 'App with Https');
  await manualStatus.start();

  await standalone.waitForTimeout(1000);
  await chatInstanceDashboard.getServerNode('master').getByTestId('refresh-processes').click();
  await expect(chatInstanceDashboard.getServerNode('master').locator('tr', { hasText: 'App with Https' }).locator('mat-icon', { hasText: 'favorite' })).toBeVisible();

  // remove process and activate to see result
  await instanceConfig.goto();
  const appWithHttpsSettings = await instanceConfig.getProcessSettingsPanel('master', 'App with Https');
  await appWithHttpsSettings.deleteProcess();
  await instanceConfig.save();

  await standalone.waitForURL(`/#/instances/dashboard/${groupId(testInfo)}/**`);
  await chatInstanceDashboard.expectOpen();
  await chatInstanceDashboard.install('3');

  // activate and cancel confirmation to check nothing happens
  let confirmationPopup = await chatInstanceDashboard.activateAndExpectConfirmationDialog();
  await confirmationPopup.shouldContainExactlyTheseProcesses(['On master: App with Https']);
  await confirmationPopup.cancel();
  await confirmationPopup.shouldNotBeVisible();
  await expect(chatInstanceDashboard.getServerNode('master').locator('tr', { hasText: 'App with Https' }).locator('mat-icon', { hasText: 'favorite' })).toBeVisible();

  // activate and confirm and check all is updated
  confirmationPopup = await chatInstanceDashboard.activateAndExpectConfirmationDialog();
  await confirmationPopup.shouldContainExactlyTheseProcesses(['On master: App with Https']);
  await confirmationPopup.ok();
  await confirmationPopup.shouldNotBeVisible();
  await chatInstanceDashboard.shouldHaveServerNodeCount(0);
});
