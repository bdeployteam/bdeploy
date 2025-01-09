import { Component, inject, TemplateRef, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, finalize, switchMap } from 'rxjs';
import { BulkOperationResultDto, UserGroupInfo } from 'src/app/models/gen.dtos';
import { ACTION_OK } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';
import { UserBulkService } from '../../services/user-bulk.service';
import {
  BdBulkOperationResultComponent
} from '../../../../core/components/bd-bulk-operation-result/bd-bulk-operation-result.component';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFormInputComponent } from '../../../../core/components/bd-form-input/bd-form-input.component';
import { FormsModule } from '@angular/forms';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-user-bulk-add-to-group',
    templateUrl: './user-bulk-add-to-group.component.html',
  imports: [BdBulkOperationResultComponent, BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdFormInputComponent, FormsModule, BdButtonComponent, AsyncPipe]
})
export class UserBulkAddToGroupComponent {
  private readonly authAdmin = inject(AuthAdminService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly bulk = inject(UserBulkService);

  protected loading$ = new BehaviorSubject<boolean>(false);
  protected userInput: string;

  protected bulkOpResult: BulkOperationResultDto;
  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;
  @ViewChild('opResult') private readonly opResult: TemplateRef<unknown>;

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
        finalize(() => this.loading$.next(false)),
      )
      .subscribe(() => {
        this.router.navigate(['..'], { relativeTo: this.route });
      });
  }
}
