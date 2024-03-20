import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { BehaviorSubject, Observable, Subscription, finalize } from 'rxjs';
import { UserGroupInfo } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'add-user-group',
  templateUrl: './add-user-group.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddUserGroupComponent implements OnInit, OnDestroy {
  private authAdmin = inject(AuthAdminService);
  private areas = inject(NavAreasService);

  protected saving$ = new BehaviorSubject<boolean>(false);
  protected addUserGroup: Partial<UserGroupInfo>;
  protected addConfirm: string;

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('form') public form: NgForm;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.addUserGroup = {};
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

  public doSave(): Observable<UserGroupInfo> {
    this.saving$.next(true);
    return this.authAdmin.createUserGroup(this.addUserGroup as UserGroupInfo);
  }
}
