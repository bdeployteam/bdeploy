import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { BehaviorSubject, Observable, Subscription, finalize } from 'rxjs';
import { UserInfo } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'add-user',
  templateUrl: './add-user.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddUserComponent implements OnInit, OnDestroy {
  private readonly authAdmin = inject(AuthAdminService);
  private readonly areas = inject(NavAreasService);

  protected saving$ = new BehaviorSubject<boolean>(false);
  protected addUser: Partial<UserInfo>;
  protected addConfirm: string;

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('form') public form: NgForm;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.addUser = {};
    this.addConfirm = '';
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return this.form.dirty;
  }

  public canSave(): boolean {
    return this.form.valid;
  }

  protected onSave() {
    this.saving$.next(true);
    this.doSave()
      .pipe(
        finalize(() => {
          this.saving$.next(false);
        }),
      )
      .subscribe(() => {
        this.areas.closePanel();
        this.subscription?.unsubscribe();
      });
  }

  public doSave(): Observable<UserInfo> {
    this.saving$.next(true);
    return this.authAdmin.createLocalUser(this.addUser as UserInfo);
  }
}
