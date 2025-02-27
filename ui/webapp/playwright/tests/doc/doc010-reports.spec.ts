import { TestInfo } from '@playwright/test';
import { test } from '@bdeploy-setup';
import { BackendApi } from '@bdeploy-backend';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { AdminPage } from '@bdeploy-pom/primary/admin/admin.page';
import { ReportsBrowserPage } from '@bdeploy-pom/primary/reports/reports-browser.page';

function groupId(testInfo: TestInfo) {
  return `Report-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId(testInfo));
  await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for report tests`);
});

test.afterEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId(testInfo));
});

test('Reports', async ({ standalone }, testInfo) => {
  await uploadProduct(standalone, groupId(testInfo), 'test-product-2-direct');
  await createInstance(standalone, groupId(testInfo), 'Report Test Instance', 'Instance to test report features', InstancePurpose.TEST, 'Demo Product', '2.0.0');

  // first screenshots of how to assign permissions to reports.
  const admin = new AdminPage(standalone);
  await admin.goto();
  const accounts = await admin.gotoUserAccountsPage();
  const details = await accounts.getUserDetails('admin');
  const assign = await details.getAssignPermissionPanel();
  await assign.fill('Report: Products In Use', 'READ');
  await assign.screenshot('Doc_Report_Assign_Permission');

  // now go on with the actual report.
  const rep = new ReportsBrowserPage(standalone);
  await rep.goto();
  await rep.screenshot('Doc_Report_Browser');

  const form = await rep.getReportFormPanel('Products In Use');
  await form.screenshot('Doc_Report_Form');

  const report = await form.generate();
  await report.getReportRowDetail('Report Test Instance');

  await report.screenshot('Doc_Report_Generated');
});
