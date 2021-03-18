import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { cloneDeep, isEqual } from 'lodash-es';
import { BehaviorSubject, Subject, Subscription } from 'rxjs';
import { debounceTime, finalize } from 'rxjs/operators';
import { UserInfo } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { BdPanelButtonComponent } from 'src/app/modules/core/components/bd-panel-button/bd-panel-button.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
  selector: 'app-edit',
  templateUrl: './edit.component.html',
  styleUrls: ['./edit.component.css'],
})
export class EditComponent implements OnInit, OnDestroy, DirtyableDialog {
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ mail$ = new BehaviorSubject<string>(null);
  /* template */ user: UserInfo;
  /* template */ orig: UserInfo;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('backButton') private back: BdPanelButtonComponent;
  private subscription: Subscription;
  private mailChanged = new Subject<string>();

  constructor(private auth: AuthenticationService, public settings: SettingsService) {
    this.subscription = this.mailChanged.pipe(debounceTime(500)).subscribe((v) => this.mail$.next(v));
  }

  ngOnInit(): void {
    this.auth.getUserInfo().subscribe((u) => {
      if (!!u) {
        this.loading$.next(false);
        this.user = cloneDeep(u);
        this.orig = cloneDeep(u);
        this.mail$.next(this.user.email);
      }
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ isDirty(): boolean {
    return !isEqual(this.user, this.orig);
  }

  /* template */ updateMail(): void {
    this.mailChanged.next(this.user.email);
  }

  /* template */ onSave(): void {
    this.loading$.next(true);
    this.auth
      .updateUserInfo(this.user)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((_) => {
        this.back.onClick();
      });
  }
}
