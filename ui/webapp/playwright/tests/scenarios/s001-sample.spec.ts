import { test } from '@bdeploy-setup';
import { LoginPage } from '@bdeploy-pom/primary/login.page';

test('S001 Sample Scenario', async ({ standalone }) => {
  await new LoginPage(standalone).goto();
});