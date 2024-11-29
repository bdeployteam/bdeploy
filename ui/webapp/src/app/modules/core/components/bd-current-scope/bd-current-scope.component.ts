import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { combineLatest, map } from 'rxjs';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';

interface LinkableScope {
  name: string;
  link: string[];
}

@Component({
    selector: 'app-bd-current-scope',
    templateUrl: './bd-current-scope.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false
})
export class BdCurrentScopeComponent {
  private readonly groups = inject(GroupsService);
  private readonly instance = inject(InstancesService);
  private readonly repositories = inject(RepositoriesService);

  protected currentScope$ = combineLatest([
    this.groups.current$,
    this.instance.current$,
    this.repositories.current$,
  ]).pipe(
    map(([g, i, r]) => {
      const group: LinkableScope = g?.name ? { name: g.title, link: ['/instances', 'browser', g.name] } : null;
      const instance: LinkableScope =
        i?.instanceConfiguration?.name && g?.name
          ? {
              name: i.instanceConfiguration.name,
              link: ['/instances', 'dashboard', g?.name, i.instanceConfiguration.id],
            }
          : null;
      const repo: LinkableScope = r?.name ? { name: r.name, link: ['/repositories', 'repository', r.name] } : null;
      return [group, instance, repo].filter((item) => item);
    }),
  );
}
