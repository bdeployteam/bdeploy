import { BackendApi } from '@bdeploy-backend';
import { expect, test } from '@bdeploy-setup';
import { createInstanceGroup, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { SystemTemplatePage } from '@bdeploy-pom/primary/system/system-template.page';
import { FormSelectElement } from '@bdeploy-elements/form-select.elements';

const sysTplGroupId = `SystemTemplateGroup`;
test.slow();

// clean out any left-over instance group from the tests
test.afterAll(async ({ standalone }) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(sysTplGroupId);
});

test('System Template UI', async ({ standalone }) => {
  await new BackendApi(standalone).mockFilterGroups(sysTplGroupId);
  await createInstanceGroup(standalone, sysTplGroupId);
  await uploadProduct(standalone, sysTplGroupId, 'test-product-1-direct');
  await uploadProduct(standalone, sysTplGroupId, 'test-product-2-direct');
  await uploadProduct(standalone, sysTplGroupId, 'chat-product-1-direct');

  // create three instances.
  const sysTpl = new SystemTemplatePage(standalone, sysTplGroupId);
  await sysTpl.goto();

  await sysTpl.screenshot('Doc_SystemTemplate_Wizard');

  await sysTpl.uploadTemplate('system-template.yaml');
  await sysTpl.checkTemplateLoaded('system-template.yaml');

  const nextBtn = sysTpl.getDialog().getByRole('button', { name: 'Next' });
  await nextBtn.click();

  await expect(sysTpl.getDialog().getByLabel('System Name')).toHaveValue('Test System');
  await new FormSelectElement(sysTpl.getDialog(), 'Purpose').selectOption('TEST');

  await nextBtn.click();
  await expect(sysTpl.getDialog().getByLabel('The Node Number')).toHaveValue('0');
  await expect(sysTpl.getDialog().getByLabel('The Node Base Name')).toHaveValue('Node');
  await sysTpl.getDialog().getByLabel('System Password').fill('XX');

  await nextBtn.click();
  await sysTpl.screenshot('Doc_SystemTemplate_InstanceTemplates');

  // fill data for demo instance
  await sysTpl.getDialog().getByLabel('Text Value').fill('Demo Text');
  await new FormSelectElement(sysTpl.getDialog(), 'Group \'Server Apps\'').selectOption('Apply to master');
  await new FormSelectElement(sysTpl.getDialog(), 'Group \'Client Apps\'').selectOption('Apply to Client Applications');

  // fill data for both chat instances.
  await sysTpl.getDialog().getByRole('tab').getByText('Chat Node 1').click();
  await new FormSelectElement(sysTpl.getDialog(), 'Group \'Chat App\'').selectOption('Apply to master');

  await sysTpl.getDialog().getByRole('tab').getByText('Chat Node 2').click();
  await new FormSelectElement(sysTpl.getDialog(), 'Group \'Chat App\'').selectOption('Apply to master');

  await nextBtn.click();
  await expect(sysTpl.getDialog().locator('app-bd-notification-card', { hasText: 'Instances have been created.' })).toBeVisible();
  await expect(sysTpl.getDialog().locator('table').locator('mat-icon', { hasText: 'check' })).toHaveCount(4);
  await sysTpl.screenshot('Doc_SystemTemplate_Done');
});
