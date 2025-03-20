import { BackendApi } from '@bdeploy-backend';
import { expect, test } from '@bdeploy-setup';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { InstancesBrowserPage } from '@bdeploy-pom/primary/instances/instances-browser.page';

const bulkGroupId = `BulkGroup`;

test.beforeEach(async ({ standalone }) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(bulkGroupId);
  await api.createGroup(bulkGroupId, `Group for bulk instance tests`);
});

test.afterEach(async ({ standalone }) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(bulkGroupId);
});

test('Bulk Instance Manipulation', async ({ standalone }) => {
  await new BackendApi(standalone).mockFilterGroups(bulkGroupId);
  await uploadProduct(standalone, bulkGroupId, 'test-product-1-direct');
  await uploadProduct(standalone, bulkGroupId, 'test-product-2-direct');

  // create three instances.
  await createInstance(standalone, bulkGroupId, 'Test Instance 1', 'Bulk Test 1', InstancePurpose.TEST, 'Demo Product', '1.0.0');
  await createInstance(standalone, bulkGroupId, 'Test Instance 2', 'Bulk Test 2', InstancePurpose.TEST, 'Demo Product', '1.0.0');
  await createInstance(standalone, bulkGroupId, 'Test Instance 3', 'Bulk Test 3', InstancePurpose.TEST, 'Demo Product', '1.0.0');

  const instances = new InstancesBrowserPage(standalone, bulkGroupId);
  await instances.goto();

  const bulk = await instances.bulkManipulation();

  // now we need to select the entries in the table.
  await instances.getTableRowContaining('Test Instance 1').locator('input[type=checkbox]').check();
  await instances.getTableRowContaining('Test Instance 2').locator('input[type=checkbox]').check();

  await instances.screenshot('Doc_InstancesBulkPanel');

  await bulk.setProductVersion('2.0.0');
  const dlg = await bulk.getLocalMessageDialog('Result');
  await expect(dlg.getByText('Created instance version 2')).toHaveCount(2);

  await instances.screenshot('Doc_InstancesBulkResult', false, false);
});
