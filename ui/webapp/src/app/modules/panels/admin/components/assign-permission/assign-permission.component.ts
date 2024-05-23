import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest, skipWhile } from 'rxjs';
import { Permission } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'assign-permission',
  templateUrl: './assign-permission.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssignPermissionComponent implements OnInit, OnDestroy {
  private authAdmin = inject(AuthAdminService);
  private areas = inject(NavAreasService);
  private groups = inject(GroupsService);
  private repositories = inject(RepositoriesService);

  protected scopes$ = new BehaviorSubject<string[]>([null]);
  protected labels$ = new BehaviorSubject<string[]>(['Global']);
  protected assignScope: string = null;
  protected assignPerm: Permission;
  protected loading$ = new BehaviorSubject<boolean>(true);
  protected allPerms: Permission[] = Object.keys(Permission).map((k) => Permission[k]);

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = combineLatest([this.groups.groups$, this.repositories.repositories$])
      .pipe(skipWhile(([g]) => !g))
      .subscribe(([groups, repositories]) => {
        const groupNames = groups.map((g) => g.instanceGroupConfiguration.name);
        const repositoryNames = repositories.map((r) => r.name);
        const sortedNames = [...groupNames, ...repositoryNames].sort((a, b) => a.localeCompare(b));

        this.scopes$.next([null, ...sortedNames]);
        this.labels$.next(['Global', ...sortedNames]);
        this.loading$.next(false);
      });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onSave() {
    const user = this.authAdmin.users$.value.find((u) => u.name === this.areas.panelRoute$.value.params['user']);
    const existing = user.permissions.find((p) => p.scope === this.assignScope);
    if (existing) {
      existing.permission = this.assignPerm;
    } else {
      user.permissions.push({
        scope: this.assignScope,
        permission: this.assignPerm,
      });
    }

    this.subscription.add(
      this.authAdmin.updateUser(user).subscribe(() => {
        this.areas.closePanel();
      }),
    );
  }
}
