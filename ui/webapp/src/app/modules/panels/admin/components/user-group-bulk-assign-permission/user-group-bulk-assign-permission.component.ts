import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, TemplateRef, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subscription, combineLatest, finalize, skipWhile, switchMap } from 'rxjs';
import { BulkOperationResultDto, Permission } from 'src/app/models/gen.dtos';
import { ACTION_OK } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { UserGroupBulkService } from '../../services/user-group-bulk.service';

@Component({
  selector: 'app-user-group-bulk-assign-permission',
  templateUrl: './user-group-bulk-assign-permission.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserGroupBulkAssignPermissionComponent implements OnInit, OnDestroy {
  private readonly groups = inject(GroupsService);
  private readonly repositories = inject(RepositoriesService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly bulk = inject(UserGroupBulkService);

  protected scopes$ = new BehaviorSubject<string[]>([null]);
  protected labels$ = new BehaviorSubject<string[]>(['Global']);
  protected assignScope: string = null;
  protected assignPerm: Permission;
  protected loading$ = new BehaviorSubject<boolean>(true);
  protected allPerms: Permission[] = Object.keys(Permission).map((k) => Permission[k]);

  protected bulkOpResult: BulkOperationResultDto;
  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;
  @ViewChild('opResult') private readonly opResult: TemplateRef<unknown>;

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
    this.loading$.next(true);
    this.bulk
      .assignPermission({ scope: this.assignScope, permission: this.assignPerm })
      .pipe(
        switchMap((r) => {
          this.bulkOpResult = r;
          return this.dialog.message({
            header: 'Result',
            template: this.opResult,
            actions: [ACTION_OK],
          });
        }),
        finalize(() => this.loading$.next(false)),
      )
      .subscribe(() => {
        this.router.navigate(['..'], { relativeTo: this.route });
      });
  }
}
