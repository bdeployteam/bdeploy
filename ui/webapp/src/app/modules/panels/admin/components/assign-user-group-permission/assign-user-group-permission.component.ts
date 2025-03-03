import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest, skipWhile } from 'rxjs';
import { Permission } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { ReportsService } from 'src/app/modules/primary/reports/services/reports.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { FormsModule } from '@angular/forms';
import { BdFormSelectComponent } from '../../../../core/components/bd-form-select/bd-form-select.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-assign-user-group-permission',
    templateUrl: './assign-user-group-permission.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, FormsModule, BdFormSelectComponent, BdButtonComponent, AsyncPipe]
})
export class AssignUserGroupPermissionComponent implements OnInit, OnDestroy {
  private readonly authAdmin = inject(AuthAdminService);
  private readonly areas = inject(NavAreasService);
  private readonly groups = inject(GroupsService);
  private readonly repositories = inject(RepositoriesService);
  private readonly reports = inject(ReportsService);

  protected scopes$ = new BehaviorSubject<string[]>([null]);
  protected labels$ = new BehaviorSubject<string[]>(['Global']);
  protected assignScope: string = null;
  protected assignPerm: Permission;
  protected loading$ = new BehaviorSubject<boolean>(true);
  protected allPerms: Permission[] = Object.values(Permission);

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = combineLatest([this.groups.groups$, this.repositories.repositories$, this.reports.reports$])
      .pipe(skipWhile(([g, r, re]) => !g || !r || !re))
      .subscribe(([g, r, re]) => {
        const groups = g
          .map((i) => ({
            label: `Group: ${i.instanceGroupConfiguration.name}`,
            scope: i.instanceGroupConfiguration.name,
          }))
          .sort((a, b) => a.label.localeCompare(b.label));
        const repositories = r
          .map((i) => ({ label: `Repository: ${i.name}`, scope: i.name }))
          .sort((a, b) => a.label.localeCompare(b.label));
        const reports = re
          .map((i) => ({ label: `Report: ${i.name}`, scope: i.type }))
          .sort((a, b) => a.label.localeCompare(b.label));
        const sorted = [...groups, ...repositories, ...reports];

        this.scopes$.next([null, ...sorted.map((item) => item.scope)]);
        this.labels$.next(['Global', ...sorted.map((item) => item.label)]);
        this.loading$.next(false);
      });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onSave() {
    const group = this.authAdmin.userGroups$.value.find((g) => g.id === this.areas.panelRoute$.value.params['group']);
    const existing = group.permissions.find((p) => p.scope === this.assignScope);
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
      }),
    );
  }
}
