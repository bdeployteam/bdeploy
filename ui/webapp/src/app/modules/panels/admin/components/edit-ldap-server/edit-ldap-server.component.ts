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
} from 'rxjs';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'edit-ldap-server',
  templateUrl: './edit-ldap-server.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditLdapServerComponent
  implements OnInit, OnDestroy, AfterViewInit, DirtyableDialog
{
  /* template */ tempServer: Partial<LDAPSettingsDto>;
  /* template */ origServer: Partial<LDAPSettingsDto>;
  /* template */ initialServer: Partial<LDAPSettingsDto>;
  /* template */ isDirty$ = new BehaviorSubject<boolean>(false);
  private subscription: Subscription;

  @ViewChild('form') public form: NgForm;
  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  constructor(
    private settings: SettingsService,
    private areas: NavAreasService
  ) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnInit(): void {
    this.subscription.add(
      combineLatest([
        this.areas.panelRoute$,
        this.settings.settings$,
      ]).subscribe(([route, settings]) => {
        this.initialServer = settings.auth.ldapSettings.find(
          (a) => a.id === route.params['id']
        );
        if (
          !settings ||
          !route?.params ||
          !route.params['id'] ||
          !this.initialServer
        ) {
          this.areas.closePanel();
          return;
        }
        this.tempServer = cloneDeep(this.initialServer);
        this.origServer = cloneDeep(this.initialServer);
      })
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
    if (this.tempServer && this.initialServer) {
      return isDirty(this.tempServer, this.initialServer);
    }
  }

  canSave(): boolean {
    return this.form.valid;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ onSave() {
    this.doSave().subscribe(() => {
      this.reset();
    });
  }

  public doSave(): Observable<void> {
    return of(
      this.settings.editLdapServer(this.tempServer, this.initialServer)
    );
  }

  private reset() {
    this.tempServer = this.initialServer;
    this.areas.closePanel();
  }
}
