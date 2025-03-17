import { AfterViewInit, ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, combineLatest, debounceTime, Observable, of, Subscription, switchMap } from 'rxjs';
import { UserInfo } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFormInputComponent } from '../../../../core/components/bd-form-input/bd-form-input.component';
import { TrimmedValidator } from '../../../../core/validators/trimmed.directive';
import { PasswordVerificationValidator } from '../../../../core/validators/password-verification.directive';
import { PasswordStrengthMeterComponent } from 'angular-password-strength-meter';
import { MatDivider } from '@angular/material/divider';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-edit-user',
    templateUrl: './edit-user.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, FormsModule, BdFormInputComponent, TrimmedValidator, PasswordVerificationValidator, PasswordStrengthMeterComponent, MatDivider, BdButtonComponent, AsyncPipe]
})
export class EditUserComponent implements OnInit, AfterViewInit, DirtyableDialog, OnDestroy {
  private readonly authAdmin = inject(AuthAdminService);
  private readonly areas = inject(NavAreasService);

  protected passConfirm: string;
  protected tempUser: UserInfo;
  protected origUser: UserInfo;
  protected loading$ = new BehaviorSubject<boolean>(true);
  protected isDirty$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;

  @ViewChild('form') public form: NgForm;
  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.passConfirm = null;
    this.subscription.add(
      combineLatest([this.areas.panelRoute$, this.authAdmin.users$]).subscribe(([route, users]) => {
        if (!users || !route?.params?.['user']) {
          return;
        }
        const user = users.find((u) => u.name === route.params['user']);
        this.tempUser = cloneDeep(user);
        this.origUser = cloneDeep(user);
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

  public isDirty(): boolean {
    return isDirty(this.tempUser, this.origUser);
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
    return this.authAdmin.updateUser(this.tempUser).pipe(
      switchMap(() => {
        if (this.tempUser.password?.length) {
          return this.authAdmin.updateLocalUserPassword(this.tempUser.name, this.tempUser.password);
        }
        return of(null);
      }),
    );
  }

  private reset() {
    this.tempUser = this.origUser;
    this.areas.closePanel();
  }
}
