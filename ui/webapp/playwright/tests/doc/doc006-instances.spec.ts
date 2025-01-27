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

test.beforeAll(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for instance tests`);
});

test.afterAll(async ({ standalone }, testInfo) => {
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
  //       the root cause.
  await standalone.waitForTimeout(100);

  await instance.install();
  await instance.activate();

  await expect(instance.getServerNode('master').locator('tr', { hasText: 'Server No Sleep' })).toBeVisible();
  await instance.screenshot('Doc_InstanceDashboardActive');
});
