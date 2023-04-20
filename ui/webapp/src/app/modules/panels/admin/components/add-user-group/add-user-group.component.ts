import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
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
  /* template */ saving$ = new BehaviorSubject<boolean>(false);
  /* template */ addUserGroup: Partial<UserGroupInfo>;
  /* template */ addConfirm: string;

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('form') public form: NgForm;

  constructor(
    private authAdmin: AuthAdminService,
    private areas: NavAreasService
  ) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnInit(): void {
    this.addUserGroup = {};
    this.addConfirm = '';
  }

  isDirty(): boolean {
    return this.form.dirty;
  }

  canSave(): boolean {
    return this.form.valid;
  }

  /* template */ onSave() {
    this.saving$.next(true);
    this.doSave()
      .pipe(
        finalize(() => {
          this.saving$.next(false);
        })
      )
      .subscribe(() => {
        this.areas.closePanel();
        this.subscription.unsubscribe();
      });
  }

  public doSave(): Observable<UserGroupInfo> {
    this.saving$.next(true);
    return this.authAdmin.createUserGroup(this.addUserGroup as UserGroupInfo);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
