import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, TemplateRef, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subscription, combineLatest, finalize, skipWhile, switchMap } from 'rxjs';
import { BulkOperationResultDto } from 'src/app/models/gen.dtos';
import { ACTION_OK } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { UserBulkService } from '../../services/user-bulk.service';

@Component({
  selector: 'app-user-bulk-remove-permission',
  templateUrl: './user-bulk-remove-permission.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserBulkRemovePermissionComponent implements OnInit, OnDestroy {
  protected bulk = inject(UserBulkService);
  private groups = inject(GroupsService);
  private repositories = inject(RepositoriesService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  protected scopes$ = new BehaviorSubject<string[]>([null]);
  protected labels$ = new BehaviorSubject<string[]>(['Global']);
  protected assignScope: string = null;
  protected loading$ = new BehaviorSubject<boolean>(true);

  protected bulkOpResult: BulkOperationResultDto;
  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild('opResult') private opResult: TemplateRef<any>;

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = combineLatest([this.groups.groups$, this.repositories.repositories$])
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

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onRemove() {
    this.loading$.next(true);
    this.bulk
      .removePermission(this.assignScope)
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
