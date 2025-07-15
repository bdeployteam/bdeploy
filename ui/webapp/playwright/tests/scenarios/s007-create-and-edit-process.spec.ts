import { BackendApi } from '@bdeploy-backend';
import { expect, test } from '@bdeploy-setup';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { TestInfo } from '@playwright/test';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';

function groupId(testInfo: TestInfo) {
  return `S007Group-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for S007`);
});

test.afterEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId(testInfo));
});

test('S007 Create and edit process', async ({ standalone }, testInfo) => {
  /*--- Chat Product  ---*/
  await uploadProduct(standalone, groupId(testInfo), 'chat-product-1-direct');
  await createInstance(standalone, groupId(testInfo), 'Chat Instance', 'S007 test', InstancePurpose.PRODUCTIVE, 'Demo Chat App', '1.0.0');

  const chatInstanceDashboard = new InstanceDashboardPage(standalone, groupId(testInfo), 'Chat Instance');
  await chatInstanceDashboard.goto();

  const instanceConfig = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Chat Instance');
  await instanceConfig.goto();

  const addProcessPanel = await instanceConfig.getAddProcessPanel('master');
  await addProcessPanel.shouldHaveOptionCount(5); // 1 app and 4 templates
  /*--- Check which template can and cannot be selected ---*/
  await addProcessPanel.checkTemplateCanBeSelected("App with Https");
  await addProcessPanel.checkTemplateCannotBeSelected("Chat App 1");
  await addProcessPanel.checkTemplateCannotBeSelected("Chat App 2");
  await addProcessPanel.checkTemplateCannotBeSelected("Chat App 3");

  await addProcessPanel.addProcess("Chat Application");
  await addProcessPanel.addProcessTemplate("App with Https");
  await instanceConfig.save();

  // check processes
  await instanceConfig.goto();
  await instanceConfig.shouldHaveNodeCount(1);
  await instanceConfig.shouldHaveControlGroupCountForNode('master', 1);
  await instanceConfig.shouldHaveProcessCountForNode('master', 2);
  await expect(instanceConfig.getProcessRow('master', 'Chat Application')).toBeVisible();
  await expect(instanceConfig.getProcessRow('master', 'App with Https')).toBeVisible();

  /*--- APP WITH HTTPS - Check defaults and change ---*/
  const appWithHttpsSettings = await instanceConfig.getProcessSettingsPanel('master', 'App with Https');
  const appWithHttpsParamPanel = await appWithHttpsSettings.getConfigureParametersPanel();
  // check default state of the params
  await appWithHttpsParamPanel.showCommandPreview();
  await expect(appWithHttpsParamPanel.getStartCommandPreview())
    .toContainText("{{WINDOWS:launch.bat}}{{LINUX:launch.sh}}{{LINUX_AARCH64:launch.sh}}-keystore.path=../some/path/keystore");
  const paramGroup = await appWithHttpsParamPanel.getParameterGroup('Test Parameters');
  await paramGroup.toggle();
  await paramGroup.selectParameters();
  await paramGroup.shouldBeSelected("https-keystore-path");
  await paramGroup.parameterHasValue("https-keystore-path", "../some/path/keystore");
  // change and check results
  await paramGroup.toggleParameter("https-keystore-path");
  await paramGroup.finishSelectParameters();
  await expect(appWithHttpsParamPanel.getStartCommandPreview())
    .toContainText("{{WINDOWS:launch.bat}}{{LINUX:launch.sh}}{{LINUX_AARCH64:launch.sh}}");
  await appWithHttpsParamPanel.apply();

  /*--- DEFAULT APP - Check defaults and change ---*/
  const chatAppSettings = await instanceConfig.getProcessSettingsPanel('master', 'App with Https');
  const chatAppParamPanel = await chatAppSettings.getConfigureParametersPanel();
  // check default state of the params
  await chatAppParamPanel.showCommandPreview();
  await expect(chatAppParamPanel.getStartCommandPreview())
    .toContainText("{{WINDOWS:launch.bat}}{{LINUX:launch.sh}}{{LINUX_AARCH64:launch.sh}}");
  const otherParamGroup = await chatAppParamPanel.getParameterGroup('Test Parameters');
  await otherParamGroup.toggle();
  await otherParamGroup.selectParameters();
  await otherParamGroup.shouldNotBeSelected("https-keystore-path");
  // change and check state now
  await otherParamGroup.toggleParameter("https-keystore-path");
  await otherParamGroup.finishSelectParameters();
  await otherParamGroup.getParameter("https-keystore-path").locator("input").nth(0).fill("some/path/value");
  await expect(chatAppParamPanel.getStartCommandPreview())
    .toContainText("{{WINDOWS:launch.bat}}{{LINUX:launch.sh}}{{LINUX_AARCH64:launch.sh}}-keystore.path=some/path/value");
  await chatAppParamPanel.apply();

  // save only works if there are no errors; goto only works if save routed you back to the dashboard
  await instanceConfig.save();
  await instanceConfig.goto();
  await instanceConfig.shouldHaveProcessCountForNode('master', 2);
});

