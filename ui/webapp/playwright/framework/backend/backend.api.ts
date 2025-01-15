import { APIRequestContext, Page } from '@playwright/test';
import { InstanceGroupConfigurationDto } from '@bdeploy/models/gen.dtos';

export class BackendApi {
  private readonly _page: Page;
  private readonly _request: APIRequestContext;

  constructor(page: Page) {
    this._page = page;
    this._request = page.request;
  }

  /** Delete a specified group from the backend */
  async deleteGroup(group: string) {
    await this._request.delete(`/api/group/${group}`);
    // ignore response status, so we can simply fire & forget at the start of each test.
  }

  /** Mock the instance group list request to behave as if there were no instance groups on the backend */
  async mockNoGroups() {
    await this._page.route('**/api/group', async route => route.fulfill({ status: 200, json: [] }));
  }

  /** Mock the instance group list request to filter out any but the given instance groups */
  async mockFilterGroups(...groups: string[]) {
    await this._page.route('**/api/group', async route => {
      if (route.request().method() !== 'GET') {
        await route.continue();
      } else {
        const real = await route.fetch();
        const json = await real.json() as InstanceGroupConfigurationDto[] || [];
        await route.fulfill({
          response: real,
          json: json.filter(g => groups.includes(g.instanceGroupConfiguration.name))
        });
      }
    });
  }
}