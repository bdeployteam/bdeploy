import { BackendApi } from '@bdeploy-backend';
import { expect, test } from '@bdeploy-setup';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { Page, TestInfo } from '@playwright/test';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { TopBar } from '@bdeploy-pom/fragments/top-bar.fragment';
import { ConfigureParametersPanel } from '@bdeploy-pom/panels/instances/process-settings/configure-parameters.panel';

function groupId(testInfo: TestInfo) {
  return `S009Group-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for S009`);
});

test.afterEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId(testInfo));
});

function grpLocator(page: Page, params: ConfigureParametersPanel, name: string) {
  return params.getDialog().locator('mat-expansion-panel', { has: page.locator('mat-expansion-panel-header', { hasText: name }) });
}

test('S007 Search parameters', async ({ standalone }, testInfo) => {
  await uploadProduct(standalone, groupId(testInfo), 'test-product-2-direct');
  await createInstance(standalone, groupId(testInfo), 'Demo Instance', 'S009 test', InstancePurpose.PRODUCTIVE, 'Demo Product', '2.0.0');

  const config = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Demo Instance');
  await config.goto();

  const add = await config.getAddProcessPanel('master');
  await add.addProcess('Server Application');

  const processSettings = await config.getProcessSettingsPanel('master', 'Server Application');
  const params = await processSettings.getConfigureParametersPanel();
  const processCustomParameters = await params.getParameterGroup('Custom Parameters');
  await processCustomParameters.toggle();

  const paramPopup = await processCustomParameters.addCustomParameter();
  await paramPopup.fill('custom.param', 'CustomValue');
  await paramPopup.ok();

  const paramPopup2 = await processCustomParameters.addCustomParameter();
  await paramPopup2.fill('another.custom', 'CustomValue');
  await paramPopup2.ok();

  const topBar = new TopBar(standalone);

  // search by ID
  await topBar.getSearchField().fill('custom.param');
  await expect(grpLocator(standalone, params, 'Test Parameters')).not.toBeAttached();
  await expect(grpLocator(standalone, params, 'Custom Parameters')).toBeAttached();

  const customGroup = await params.getParameterGroup('Custom Parameters');
  await expect(customGroup.getParameter('custom.param')).toBeAttached();
  await expect(customGroup.getParameter('another.custom')).not.toBeAttached();

  // search by Value
  await topBar.getSearchField().fill('CustomValue');
  await expect(grpLocator(standalone, params, 'Test Parameters')).not.toBeAttached();
  await expect(grpLocator(standalone, params, 'Custom Parameters')).toBeAttached();

  await expect(customGroup.getParameter('custom.param')).toBeAttached();
  await expect(customGroup.getParameter('another.custom')).toBeAttached();

  // search by Name
  await topBar.getSearchField().fill('Fixed');
  await expect(grpLocator(standalone, params, 'Test Parameters')).toBeAttached();
  await expect(grpLocator(standalone, params, 'Custom Parameters')).not.toBeAttached();
  const testGroup = await params.getParameterGroup('Test Parameters');
  await testGroup.toggle();

  await expect(testGroup.getParameter('param.fixed')).toBeAttached();
  await expect(testGroup.getParameter('param.boolean')).not.toBeAttached();
});

