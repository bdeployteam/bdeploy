import { Page } from '@playwright/test';
import { test } from '@bdeploy-setup';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';
import { LoginPage } from '@bdeploy-pom/primary/login.page';

const commonTest = async (page: Page, expectedType: string) => {
  const login = new LoginPage(page);

  await login.goto();
  await login.login('admin', 'adminadminadmin');

  const mainMenu = new MainMenu(page);
  await mainMenu.expandMainMenu();
  await mainMenu.expectServerType(expectedType);
};

test('F001 Login (Standalone)', async ({ standalone }) => {
  await commonTest(standalone, 'STANDALONE');
});

test('F001 Login (Central)', async ({ central }) => {
  await commonTest(central, 'CENTRAL');
});

test('F001 Login (Managed)', async ({ managed }) => {
  await commonTest(managed, 'MANAGED');
});
