import { Component, inject } from '@angular/core';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ServersColumnsService } from 'src/app/modules/primary/servers/services/servers-columns.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

@Component({
    selector: 'app-select-managed-server',
    templateUrl: './select-managed-server.component.html',
    standalone: false
})
export class SelectManagedServerComponent {
  private readonly areas = inject(NavAreasService);
  protected readonly servers = inject(ServersService);
  protected readonly columns = inject(ServersColumnsService);

  protected getRecordRoute = (row: ManagedMasterDto) => {
    // calculate relative route, as this component is used from two different routes.
    const allRoutes = this.areas.panelRoute$.value.pathFromRoot;
    const oldRoute = allRoutes
      .map((r) => r.url)
      .reduce((a, v) => a.concat(v), [])
      .map((s) => s.path);
    return [
      '',
      {
        outlets: {
          panel: [...oldRoute.slice(0, oldRoute.length - 1), row.hostName],
        },
      },
    ];
  };
}
