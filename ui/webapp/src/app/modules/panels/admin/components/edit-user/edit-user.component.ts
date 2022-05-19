import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import {
  BehaviorSubject,
  combineLatest,
  debounceTime,
  Observable,
  of,
  Subscription,
  switchMap,
} from 'rxjs';
import { UserInfo } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'edit-user',
  templateUrl: './edit-user.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditUserComponent
  implements OnInit, AfterViewInit, DirtyableDialog, OnDestroy
{
  /* template */ passConfirm: string;
  /* template */ tempUser: UserInfo;
  /* template */ origUser: UserInfo;
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ isDirty$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;

  @ViewChild('form') public form: NgForm;
  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  constructor(
    private authAdmin: AuthAdminService,
    private areas: NavAreasService
  ) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnInit(): void {
    this.passConfirm = null;
    this.subscription.add(
      combineLatest([this.areas.panelRoute$, this.authAdmin.users$]).subscribe(
        ([route, users]) => {
          if (!users || !route?.params || !route.params['user']) {
            return;
          }
          const user = users.find((u) => u.name === route.params['user']);
          this.tempUser = cloneDeep(user);
          this.origUser = cloneDeep(user);
          this.loading$.next(false);
        }
      )
    );
  }

  ngAfterViewInit(): void {
    if (!this.form) {
      return;
    }
    this.subscription.add(
      this.form.valueChanges.pipe(debounceTime(100)).subscribe(() => {
        this.isDirty$.next(this.isDirty());
      })
    );
  }

  isDirty() {
    return isDirty(this.tempUser, this.origUser);
  }

  /* template */ updateDirty() {
    this.isDirty$.next(this.isDirty());
  }

  /* template */ onSave() {
    this.doSave().subscribe(() => {
      this.reset();
    });
  }

  public doSave(): Observable<any> {
    return this.authAdmin.updateUser(this.tempUser).pipe(
      switchMap(() => {
        if (this.tempUser.password?.length) {
          return this.authAdmin.updateLocalUserPassword(
            this.tempUser.name,
            this.tempUser.password
          );
        }
        return of(null);
      })
    );
  }

  private reset() {
    this.tempUser = this.origUser;
    this.areas.closePanel();
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
