import { APIRequestContext, BrowserContext, Page, Response } from '@playwright/test';
import {
  InstanceGroupConfiguration,
  InstanceGroupConfigurationDto,
  ObjectChangeDto,
  ObjectChangeType
} from '@bdeploy/models/gen.dtos';

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

    await this._page.context().routeWebSocket('**/ws/object-changes', async ws => {
      const server = ws.connectToServer();
      server.onMessage(m => {
        const dto = JSON.parse(m.toString()) as ObjectChangeDto;
        if (dto.scope.scope.length < 1) {
          ws.send(m);
        }
        if (groups.includes(dto.scope.scope[0])) {
          ws.send(m);
        }
        // otherwise swallow the message - it is meant for filtered instance groups.
      });
    });
  }

  /**
   * Prevents any server actions to reach the webapp. this causes the webapp to solely act/react
   * on things it started itself (i.e. the REST requests) instead of waiting for the server to
   * notify that actions actually finished.
   * <p>
   * The main purpose of actions is to keep *other* frontend instances informed on what is going
   * on. There are timeouts and debounces attached to this. For the user it feels natural and fast,
   * however UI tests are faster. Waiting for actions on the server to be properly closed and then
   * sent via websocket to the frontends introduces extensive wait times in areas where they might
   * be not only undesirable but also hindering fast tests.
   * <p>
   * As positive side effect this will also get rid of the main menu action spinner that is on the
   * screenshots randomly depending on timing.
   */
  static async mockRemoveActions(context: BrowserContext) {
    await context.routeWebSocket('**/ws/object-changes', async ws => {
      const server = ws.connectToServer();
      server.onMessage(m => {
        const dto = JSON.parse(m.toString()) as ObjectChangeDto;
        if (dto.type === ObjectChangeType.SERVER_ACTIONS) {
          // swallow :)
          return;
        }
        ws.send(m);
      });
    });
  }
}