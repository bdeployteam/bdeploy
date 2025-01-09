import { ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, combineLatest, finalize, skipWhile, Subscription, switchMap } from 'rxjs';
import { BulkOperationResultDto, Permission } from 'src/app/models/gen.dtos';
import { ACTION_OK } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { UserBulkService } from '../../services/user-bulk.service';
import {
  BdBulkOperationResultComponent
} from '../../../../core/components/bd-bulk-operation-result/bd-bulk-operation-result.component';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { FormsModule } from '@angular/forms';
import { BdFormSelectComponent } from '../../../../core/components/bd-form-select/bd-form-select.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-user-bulk-assign-permission',
    templateUrl: './user-bulk-assign-permission.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BdBulkOperationResultComponent, BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, FormsModule, BdFormSelectComponent, BdButtonComponent, AsyncPipe]
})
export class UserBulkAssignPermissionComponent implements OnInit, OnDestroy {
  private readonly groups = inject(GroupsService);
  private readonly repositories = inject(RepositoriesService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly bulk = inject(UserBulkService);

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
