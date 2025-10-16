import { AfterViewInit, Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Observable, Subject, Subscription } from 'rxjs';
import { debounceTime, finalize, first, skipWhile } from 'rxjs/operators';
import { UserInfo } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';

import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { UserAvatarComponent } from '../../../../../core/components/user-avatar/user-avatar.component';
import { BdFormInputComponent } from '../../../../../core/components/bd-form-input/bd-form-input.component';
import { TrimmedValidator } from '../../../../../core/validators/trimmed.directive';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
  selector: 'app-edit',
  templateUrl: './edit.component.html',
  imports: [
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    FormsModule,
    UserAvatarComponent,
    BdFormInputComponent,
    TrimmedValidator,
    BdButtonComponent,
    AsyncPipe,
  ],
})
export class EditComponent implements OnInit, OnDestroy, DirtyableDialog, AfterViewInit {
  private readonly auth = inject(AuthenticationService);
  private readonly areas = inject(NavAreasService);

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected mail$ = new BehaviorSubject<string>(null);
  protected user: UserInfo;
  protected orig: UserInfo;
  protected disableSave: boolean;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;
  @ViewChild('form') public form: NgForm;
  private subscription: Subscription;
  private readonly mailChanged = new Subject<string>();

  ngOnInit(): void {
    this.subscription = this.mailChanged.pipe(debounceTime(500)).subscribe((v) => this.mail$.next(v));
    this.subscription.add(this.areas.registerDirtyable(this, 'panel'));
    this.auth
      .getUserInfo()
      .pipe(
        skipWhile((u) => !u),
        first(),
        finalize(() => this.loading$.next(false))
      )
      .subscribe((u) => {
        if (u) {
          this.user = cloneDeep(u);
          this.orig = cloneDeep(u);
          this.mail$.next(this.user.email);
        }
      });
  }

  ngAfterViewInit(): void {
    if (!this.form) {
      return;
    }
    this.subscription.add(
      this.form.valueChanges.pipe(debounceTime(100)).subscribe(() => {
        this.disableSave = this.isDirty();
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return isDirty(this.user, this.orig);
  }

  protected onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public doSave(): Observable<object> {
    this.loading$.next(true);
    this.orig = cloneDeep(this.user);
    return this.auth.updateCurrentUser(this.user).pipe(finalize(() => this.loading$.next(false)));
  }

  protected updateMail(): void {
    this.mailChanged.next(this.user.email);
  }
}
