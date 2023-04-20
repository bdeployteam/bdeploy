import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest, skipWhile } from 'rxjs';
import { Permission } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'assign-user-group-permission',
  templateUrl: './assign-user-group-permission.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssignUserGroupPermissionComponent implements OnInit, OnDestroy {
  /* template */ scopes$ = new BehaviorSubject<string[]>([null]);
  /* template */ labels$ = new BehaviorSubject<string[]>(['Global']);
  /* template */ assignScope: string = null;
  /* template */ assignPerm: Permission;
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ allPerms: Permission[] = Object.keys(Permission).map(
    (k) => Permission[k]
  );

  private subscription: Subscription;

  constructor(
    private authAdmin: AuthAdminService,
    private areas: NavAreasService,
    private groups: GroupsService,
    private repositories: RepositoriesService
  ) {}

  ngOnInit(): void {
    this.subscription = combineLatest([
      this.groups.groups$,
      this.repositories.repositories$,
    ])
      .pipe(skipWhile(([g]) => !g))
      .subscribe(([groups, repositories]) => {
        const groupNames = groups.map((g) => g.instanceGroupConfiguration.name);
        const repositoryNames = repositories.map((r) => r.name);
        const sortedNames = [...groupNames, ...repositoryNames].sort();

        this.scopes$.next([null, ...sortedNames]);
        this.labels$.next(['Global', ...sortedNames]);
        this.loading$.next(false);
      });
  }

  /* template */ onSave() {
    const group = this.authAdmin.userGroups$.value.find(
      (g) => g.id === this.areas.panelRoute$.value.params['group']
    );
    const existing = group.permissions.find(
      (p) => p.scope === this.assignScope
    );
    if (existing) {
      existing.permission = this.assignPerm;
    } else {
      group.permissions.push({
        scope: this.assignScope,
        permission: this.assignPerm,
      });
    }

    this.subscription.add(
      this.authAdmin.updateUserGroup(group).subscribe(() => {
        this.areas.closePanel();
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
