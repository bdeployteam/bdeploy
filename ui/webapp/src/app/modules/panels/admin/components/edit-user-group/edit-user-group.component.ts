import { AfterViewInit, ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, combineLatest, debounceTime, Observable, Subscription } from 'rxjs';
import { UserGroupInfo } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFormInputComponent } from '../../../../core/components/bd-form-input/bd-form-input.component';
import { IdentifierValidator } from '../../../../core/validators/identifier.directive';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-edit-user-group',
    templateUrl: './edit-user-group.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, FormsModule, BdFormInputComponent, IdentifierValidator, BdButtonComponent, AsyncPipe]
})
export class EditUserGroupComponent implements OnInit, AfterViewInit, DirtyableDialog, OnDestroy {
  private readonly authAdmin = inject(AuthAdminService);
  private readonly areas = inject(NavAreasService);

  protected passConfirm: string;
  protected tempGroup: UserGroupInfo;
  protected origGroup: UserGroupInfo;
  protected loading$ = new BehaviorSubject<boolean>(true);
  protected isDirty$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;

  @ViewChild('form') public form: NgForm;
  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.passConfirm = null;
    this.subscription.add(
      combineLatest([this.areas.panelRoute$, this.authAdmin.userGroups$]).subscribe(([route, groups]) => {
        if (!groups || !route?.params?.['group']) {
          return;
        }
        const group = groups.find((g) => g.id === route.params['group']);
        this.tempGroup = cloneDeep(group);
        this.origGroup = cloneDeep(group);
        this.loading$.next(false);
      }),
    );
  }

  ngAfterViewInit(): void {
    if (!this.form) {
      return;
    }
    this.subscription.add(
      this.form.valueChanges.pipe(debounceTime(100)).subscribe(() => {
        this.isDirty$.next(this.isDirty());
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty() {
    return isDirty(this.tempGroup, this.origGroup);
  }

  protected updateDirty() {
    this.isDirty$.next(this.isDirty());
  }

  protected onSave() {
    this.doSave().subscribe(() => {
      this.reset();
    });
  }

  public doSave(): Observable<unknown> {
    return this.authAdmin.updateUserGroup(this.tempGroup);
  }

  private reset() {
    this.tempGroup = this.origGroup;
    this.areas.closePanel();
  }
}
