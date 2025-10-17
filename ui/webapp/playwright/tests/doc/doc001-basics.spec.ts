import { expect, test } from '@bdeploy-setup';
import { BackendApi } from '@bdeploy-backend';
import { InstanceGroupsBrowserPage } from '@bdeploy-pom/primary/groups/groups-browser.page';
import { MainMenu } from '@bdeploy-pom/fragments/main-menu.fragment';
import { LoginPage } from '@bdeploy-pom/primary/login.page';
import { TopBar } from '@bdeploy-pom/fragments/top-bar.fragment';
import { AdminPage } from '@bdeploy-pom/primary/admin/admin.page';

// tests can run in parallel as they mock an empty server.

test('Login', async ({ standalone }) => {
  const login = new LoginPage(standalone);
  await login.goto();
  await login.screenshot('Doc_Login');
});

test('Main Menu', async ({ standalone }) => {
  await new BackendApi(standalone).mockNoGroups();
  const groups = new InstanceGroupsBrowserPage(standalone);
  await groups.goto();
  await new MainMenu(standalone).expandMainMenu();

  await expect(groups.getDialog()).toContainText('Welcome to BDeploy');
  await groups.screenshot('Doc_MainMenu');
});

test('User Settings Panel', async ({ standalone }) => {
  await new BackendApi(standalone).mockNoGroups();
  const groups = new InstanceGroupsBrowserPage(standalone);
  await groups.goto();
  await new TopBar(standalone).getUserSettings();

  await groups.screenshot('Doc_UserSettings');
});

test('Search Bar', async ({ standalone }) => {
  await new BackendApi(standalone).mockNoGroups();
  const groups = new InstanceGroupsBrowserPage(standalone);
  await groups.goto();

  const topBar = new TopBar(standalone);
  await expect(topBar.getSearchField()).toBeDisabled();
  await topBar.screenshot('Doc_SearchBarDisabled');

  const admin = new AdminPage(standalone);
  await admin.goto();
  await expect(topBar.getSearchField()).toBeEnabled();
  await topBar.screenshot('Doc_SearchBarEnabled');
});