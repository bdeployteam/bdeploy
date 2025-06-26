import { BackendApi } from '@bdeploy-backend';
import { expect, test } from '@bdeploy-setup';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { TestInfo } from '@playwright/test';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { LinkExpressionPicker } from '@bdeploy-pom/panels/instances/process-settings/link-expression-picker.popup';

function groupId(testInfo: TestInfo) {
  return `S010Group-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for S010`);
});

test.afterEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId(testInfo));
});

test('S010 custom parameter content assist', async ({ standalone }, testInfo) => {
  await uploadProduct(standalone, groupId(testInfo), 'test-product-2-direct');
  await createInstance(standalone, groupId(testInfo), 'Demo Instance', 'S010 test', InstancePurpose.PRODUCTIVE, 'Demo Product', '2.0.0');

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

  const testParams = await params.getParameterGroup('Test Parameters');
  await testParams.toggle();
  await testParams.selectParameters();
  await testParams.getParameter('param.text').locator('mat-icon', { hasText: 'add' }).click();
  await testParams.finishSelectParameters();

  // scroll down a little so we can get the content assist below the input.
  await (await params.getParameterGroup('Custom Parameters')).scrollIntoView();

  // click the "link expression" toggle
  const textParam = testParams.getParameter('param.text');
  await textParam.getByRole('radio').nth(1).click();
  const linkInput = textParam.locator('id=param.text_link');
  await linkInput.focus();
  await linkInput.pressSequentially('{{V:custom.');

  await expect(params.getOverlayContainer().getByTestId('content-assist-menu').getByText('{{V:custom.param}}')).toBeAttached();

  await linkInput.press('Escape');
  await linkInput.clear();

  // open the picker - should probably move to a page object at some point? careful for the index, this icon appears twice
  await textParam.locator('mat-icon', { hasText: 'data_object' }).nth(1).click();
  const picker = new LinkExpressionPicker(textParam);
  await picker.switchTo('Process Parameters');
  await picker.getTableRowContaining('This Application').locator('mat-icon', { hasText: 'chevron_right' }).click();
  await expect(picker.getTableRowContaining('custom.param')).toBeVisible();
  await picker.getTableRowContaining('custom.param').click();
  await expect(linkInput).toHaveValue('{{V:custom.param}}');

  await processCustomParameters.selectParameters();
  await processCustomParameters.getParameter('custom.param').locator('mat-icon', { hasText: 'delete' }).click();
  // no need to call finishSelection if we deleted the last parameter.

  await linkInput.focus();
  await linkInput.pressSequentially('{{V:custom.');

  await expect(params.getOverlayContainer().getByTestId('content-assist-menu').getByText('{{V:custom.param}}')).not.toBeAttached();

  await linkInput.clear();
  await textParam.locator('mat-icon', { hasText: 'data_object' }).nth(1).click();
  await picker.switchTo('Process Parameters');
  await picker.getTableRowContaining('This Application').locator('mat-icon', { hasText: 'chevron_right' }).click();
  await expect(picker.getTableRowContaining('custom.param')).not.toBeVisible();
});

