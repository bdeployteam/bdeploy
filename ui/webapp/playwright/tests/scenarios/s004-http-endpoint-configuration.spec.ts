import { expect, test } from '@bdeploy-setup';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';
import { BackendApi } from '@bdeploy-backend';
import { TestInfo } from '@playwright/test';

function groupId(testInfo: TestInfo) {
  return `S004Group-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for S004`);
});

test.afterEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId(testInfo));
});

test('S004 Application Http Endpoints Configuration', async ({ standalone }, testInfo) => {
  const page = standalone;
  const group = groupId(testInfo);

  await uploadProduct(page, group, 'test-product-2-direct');

  /*--- Create Instance for product ---*/
  const instanceName = 'Instance';
  await createInstance(page, group, instanceName, 'Test Instance', InstancePurpose.TEST, 'Demo Product', '2.0.0');
  const instance = new InstanceDashboardPage(page, group, instanceName);
  await instance.goto();
  const config = new InstanceConfigurationPage(page, group, instanceName);
  await config.goto();

  /*--- Add process that uses the endpoint and navigate to the endpoint config ---*/
  const processName = 'Server With Sleep';
  const process = await config.getAddProcessPanel('master');
  await process.addProcessTemplate(processName);
  const varDlg = process.getVariableTemplatePopup();
  await varDlg.confirm();

  let processSettings = await config.getProcessSettingsPanel('master', 'Server With Sleep');
  let eps = await processSettings.getConfigureEndpointsPanel();
  let testEndpoint = await eps.getEndpointArea('test-api');
  await testEndpoint.scrollIntoView();

  /*--- Check it's enabled and all fields have been loaded correctly ---*/
  await expect(testEndpoint.getRawPath()).toContainText('test-api - test-api/v{{X:test.api.version}} ({{X:test.api.context}})');
  await expect(testEndpoint.getProcessedPath()).toContainText(' test-api/v12 (instance-context)');
  await expect(testEndpoint.getDisabledReason()).not.toBeAttached();

  await testEndpoint.getField('Port').shouldHaveValueAndPreview('{{X:test.api.port}}', '1234');
  await testEndpoint.getField('Secure (Use HTTPS)').shouldHaveValueAndPreview('{{X:test.api.secure}}', 'true');
  await testEndpoint.getField('Trust Store Path').shouldHaveValueAndPreview('{{X:test.api.trustStore}}', 'instance-trust-store');
  await testEndpoint.getField('Trust Store Password').shouldHaveValueAndPreview('{{X:test.api.trustStorePass}}', '*****************************');

  const authentication = testEndpoint.getField('Authentication');
  await authentication.shouldHaveValueAndPreview('{{X:test.api.authType}}', 'OIDC');
  await testEndpoint.getField('Token URL').shouldHaveValueAndPreview('{{X:test.api.tokenUrl}}', 'instance-token-url');
  await testEndpoint.getField('Client ID').shouldHaveValueAndPreview('{{X:test.api.clientId}}', 'instance-client-id');
  await testEndpoint.getField('Client Secret').shouldHaveValueAndPreview('{{X:test.api.clientSecret}}', '**********************');

  /*--- Switch authentication type and check user/pass fields appear ---*/
  await authentication.selectValue('BASIC');
  await testEndpoint.getField('Token URL').shouldNotExist();
  await testEndpoint.getField('Client ID').shouldNotExist();
  await testEndpoint.getField('Client Secret').shouldNotExist();

  await testEndpoint.getField('User').shouldBeEmpty()
  await testEndpoint.getField('User').setLinkExpression('{{X:test.api.user}}');
  await testEndpoint.getField('Password').shouldBeEmpty();
  await testEndpoint.getField('Password').setLinkExpression('{{X:test.api.password}}');

  /*--- Check 'Trust all' disables the trust store fields ---*/
  await testEndpoint.getCheckBox('Trust All Certificates').check();
  await testEndpoint.getField('Trust Store Path').shouldBeDisabled();
  await testEndpoint.getField('Trust Store Password').shouldBeDisabled();
  await eps.apply();

  /*--- Disable endpoint ---*/
  const settings = await config.getSettingsPanel();
  const varPanel = await settings.getInstanceVariablePanel();
  const variableGroup = await varPanel.getVariableGroup('Ungrouped Variables');
  await variableGroup.toggle();
  await variableGroup.getField("API enabled").toggle();
  await varPanel.apply();
  // XXX: if I save latter, the process config is not saved
  await config.save();

  /*--- Check fields are disabled ---*/
  await config.goto();
  processSettings = await config.getProcessSettingsPanel('master', 'Server With Sleep');
  eps = await processSettings.getConfigureEndpointsPanel();
  testEndpoint = await eps.getEndpointArea('test-api');
  await testEndpoint.scrollIntoView();
  await expect(testEndpoint.getDisabledReason()).toContainText('This endpoint is disabled due to a missing prerequisite ({{X:test.api.enabled}}).');
  await testEndpoint.getField('Port').shouldBeDisabled();

  /*--- Shallow check that this does not break ---*/
  await eps.getBackToOverviewButton().click();
  await instance.goto();
  await instance.install('2');
});
