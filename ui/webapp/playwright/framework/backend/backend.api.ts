import { APIRequestContext, Page, Response } from '@playwright/test';
import { InstanceGroupConfiguration, InstanceGroupConfigurationDto } from '@bdeploy/models/gen.dtos';

export class BackendApi {
  private readonly _page: Page;
  private readonly _request: APIRequestContext;

  constructor(page: Page) {
    this._page = page;
    this._request = page.request;
  }

  /** Wait for the instance group creation call to happen and complete */
  async waitForGroupPut() {
    return this._page.waitForResponse((response: Response) => response.url().includes('/api/group') && response.request().method() === 'PUT');
  }

  async createGroup(group: string, description: string) {
    const grp: Partial<InstanceGroupConfiguration> = {
      name: group,
      title: group,
      description: description,
      autoDelete: false
    };
    return this._request.put(`/api/group/`, { data: grp });
  }

  /** Delete a specified group from the backend */
  async deleteGroup(group: string) {
    await this._request.delete(`/api/group/${group}`);
    // ignore response status, so we can simply fire & forget at the start of each test.
  }

  /** Mock the instance group list request to behave as if there were no instance groups on the backend */
  async mockNoGroups() {
    await this._page.context().route('**/api/group', async route => route.fulfill({ status: 200, json: [] }));
  }

  /** Mock the instance group list request to filter out any but the given instance groups */
  async mockFilterGroups(...groups: string[]) {
    await this._page.context().route('**/api/group', async route => {
      if (route.request().method() !== 'GET') {
        await route.continue();
      } else {
        const real = await route.fetch();
        const json = await real.json() as InstanceGroupConfigurationDto[] || [];
        const filtered = json.filter(g => groups.includes(g.instanceGroupConfiguration.name));

        await route.fulfill({
          response: real,
          json: filtered
        });
      }
    });
  }
}