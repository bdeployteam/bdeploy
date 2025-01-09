import { central, managed, standalone } from './shared/test-servers';
import { expect, test } from './shared/test-setup';

test('standalone title', async ({ page }) => {
  await page.goto(standalone('/'));
  await expect(page).toHaveTitle(/BDeploy/);
});

test('central title', async ({ page }) => {
  await page.goto(central('/'));
  await expect(page).toHaveTitle(/BDeploy/);
});

test('managed title', async ({ page }) => {
  await page.goto(managed('/'));
  await expect(page).toHaveTitle(/BDeploy/);
});
