import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Observable, Subject, Subscription } from 'rxjs';
import { debounceTime, finalize, first, skipWhile } from 'rxjs/operators';
import { UserInfo } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';

@Component({
  selector: 'app-edit',
  templateUrl: './edit.component.html',
})
export class EditComponent implements OnInit, OnDestroy, DirtyableDialog, AfterViewInit {
  private auth = inject(AuthenticationService);
  private areas = inject(NavAreasService);
  protected settings = inject(SettingsService);

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected mail$ = new BehaviorSubject<string>(null);
  protected user: UserInfo;
  protected orig: UserInfo;
  protected disableSave: boolean;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChild('form') public form: NgForm;
  private subscription: Subscription;
  private mailChanged = new Subject<string>();

  ngOnInit(): void {
    this.subscription = this.mailChanged.pipe(debounceTime(500)).subscribe((v) => this.mail$.next(v));
    this.subscription.add(this.areas.registerDirtyable(this, 'panel'));
    this.auth
      .getUserInfo()
      .pipe(
        skipWhile((u) => !u),
        first(),
        finalize(() => this.loading$.next(false)),
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
      }),
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

  public doSave(): Observable<any> {
    this.loading$.next(true);
    this.orig = cloneDeep(this.user);
    return this.auth.updateUserInfo(this.user).pipe(finalize(() => this.loading$.next(false)));
  }

  protected updateMail(): void {
    this.mailChanged.next(this.user.email);
  }
}
