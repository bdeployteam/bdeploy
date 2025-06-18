import { TestInfo } from '@playwright/test';
import { expect, test } from '@bdeploy-setup';
import { ReposBrowserPage } from '@bdeploy-pom/primary/repos/repos-browser.page';
import { ReposSoftwarePage } from '@bdeploy-pom/primary/repos/repos-software.page';
import { createInstanceGroup } from '@bdeploy-pom/common/common-tasks';
import { GroupsProductsPage } from '@bdeploy-pom/primary/groups/groups-products.page';
import { BackendApi } from '@bdeploy-backend';

function repoId(testInfo: TestInfo) {
  return `Repo-${testInfo.workerIndex}`;
}

function groupId(testInfo: TestInfo) {
  return `RepoGroup-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteRepo(repoId(testInfo));
  await api.deleteGroup(groupId(testInfo));
})

test.afterEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteRepo(repoId(testInfo));
  await api.deleteGroup(groupId(testInfo));
});

test('Software Repositories', async ({ standalone }, testInfo) => {
  const browser = new ReposBrowserPage(standalone);
  await browser.goto();

  const addPanel = await browser.getAddRepoPanel();
  await addPanel.fill(repoId(testInfo), 'Test Repository');
  await addPanel.save();

  await expect(browser.getTableRowContaining(repoId(testInfo))).toBeVisible();
  await browser.screenshot('Doc_SoftwareRepo');

  const sw = new ReposSoftwarePage(standalone, repoId(testInfo));
  await sw.goto();

  const upload = await sw.getUploadPanel();
  await upload.upload('test-product-2-direct.zip');

  const state = upload.getUploadState('test-product-2-direct');
  await expect(state).toContainText('Success');

  await expect(sw.getSoftwareRow('io.bdeploy/demo/product')).toBeVisible();

  await createInstanceGroup(standalone, groupId(testInfo));
  const groupProd = new GroupsProductsPage(standalone, groupId(testInfo));
  await groupProd.goto();

  const impPanel = await groupProd.openProductImportPanel();
  await impPanel.screenshot('Doc_ImportProduct_SelectRepo');
  await impPanel.selectRepo(repoId(testInfo));
  await impPanel.screenshot('Doc_ImportProduct_SelectProduct');
  await impPanel.selectProduct('Demo Product'); // not using the ID but the user-friendly name.
  await impPanel.selectVersion('2.0.0');
  await impPanel.screenshot('Doc_ImportProduct_SelectVersion');
  await impPanel.transfer();

  await expect(groupProd.getProductRow('2.0.0')).toBeVisible();
  await groupProd.screenshot('Doc_ImportProduct_Success');
});

test('Runtime Dependencies', async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.createRepo(repoId(testInfo), 'Test Repository');
  await api.mockFilterGroups();

  const sw = new ReposSoftwarePage(standalone, repoId(testInfo));
  await sw.goto();

  const upload = await sw.getUploadPanel();
  await upload.upload('external-software-hive.zip');
  await expect(upload.getUploadState('external-software-hive')).toContainText('Success');
  await expect(sw.getSoftwareRow('external/software/linux').getByText('v1.0.0')).toBeVisible();
  await expect(sw.getSoftwareRow('external/software/windows').getByText('v1.0.0')).toBeVisible();
  await upload.upload('external-software-2-raw-direct.zip');

  const details = upload.getUploadDetailsArea('external-software-2-raw-direct');
  await details.fill('external/software/two', 'v2.0.1', true, false, false);
  await upload.screenshot('Doc_SoftwareRepoFillInfo');
  await details.import();
  await expect(sw.getSoftwareRow('external/software/two').getByText('v2.0.1')).toBeVisible();

  await upload.screenshot('Doc_SoftwareRepoUploadSuccess');

  await upload.upload('test-product-2-direct.zip');
  await expect(upload.getUploadState('test-product-2-direct')).toContainText('Success');
  await expect(sw.getSoftwareRow('io.bdeploy/demo/product')).toBeVisible();

  await sw.getSoftwareDetailsPanel('io.bdeploy/demo/product');
  await sw.screenshot('Doc_SoftwareRepoDetails');

  const settings = await sw.getRepoSettingsPanel();
  const perm = await settings.getRepoPermissionsPanel();

  await perm.screenshot('Doc_SoftwareRepoPermissions');
});