import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Observable, Subscription, combineLatest, debounceTime } from 'rxjs';
import { UserGroupInfo } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'edit-user-group',
  templateUrl: './edit-user-group.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditUserGroupComponent implements OnInit, AfterViewInit, DirtyableDialog, OnDestroy {
  private authAdmin = inject(AuthAdminService);
  private areas = inject(NavAreasService);

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
        if (!groups || !route?.params || !route.params['group']) {
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

  public doSave(): Observable<any> {
    return this.authAdmin.updateUserGroup(this.tempGroup);
  }

  private reset() {
    this.tempGroup = this.origGroup;
    this.areas.closePanel();
  }
}
