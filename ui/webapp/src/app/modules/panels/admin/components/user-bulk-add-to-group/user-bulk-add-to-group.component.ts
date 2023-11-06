import { Component, TemplateRef, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, finalize, switchMap } from 'rxjs';
import { BulkOperationResultDto, UserGroupInfo } from 'src/app/models/gen.dtos';
import { ACTION_OK } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';
import { UserBulkService } from '../../services/user-bulk.service';

@Component({
  selector: 'app-user-bulk-add-to-group',
  templateUrl: './user-bulk-add-to-group.component.html',
})
export class UserBulkAddToGroupComponent {
  private authAdmin = inject(AuthAdminService);
  protected bulk = inject(UserBulkService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  protected loading$ = new BehaviorSubject<boolean>(false);
  protected userInput: string;

  protected bulkOpResult: BulkOperationResultDto;
  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild('opResult') private opResult: TemplateRef<any>;

  protected get suggestions(): string[] {
    return this.authAdmin.userGroups$.value?.map((g) => g.name);
  }

  protected get group(): UserGroupInfo {
    return this.authAdmin.userGroups$.value?.find((g) => g.name === this.userInput);
  }

  protected add() {
    this.loading$.next(true);
    this.bulk
      .addToGroup(this.group.id)
      .pipe(
        switchMap((r) => {
          this.bulkOpResult = r;
          return this.dialog.message({
            header: 'Result',
            template: this.opResult,
            actions: [ACTION_OK],
          });
        }),
        finalize(() => this.loading$.next(false))
      )
      .subscribe(() => {
        this.router.navigate(['..'], { relativeTo: this.route });
      });
  }
}
