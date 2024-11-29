import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Observable, Subscription, combineLatest, debounceTime, of } from 'rxjs';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';

@Component({
    selector: 'app-edit-ldap-server',
    templateUrl: './edit-ldap-server.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false
})
export class EditLdapServerComponent implements OnInit, OnDestroy, AfterViewInit, DirtyableDialog {
  private readonly settings = inject(SettingsService);
  private readonly areas = inject(NavAreasService);

  protected tempServer: Partial<LDAPSettingsDto>;
  protected initialServer: Partial<LDAPSettingsDto>;
  protected isDirty$ = new BehaviorSubject<boolean>(false);
  private subscription: Subscription;

  @ViewChild('form') public form: NgForm;
  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      combineLatest([this.areas.panelRoute$, this.settings.settings$]).subscribe(([route, settings]) => {
        this.initialServer = settings.auth.ldapSettings.find((a) => a.id === route.params['id']);
        if (!settings || !route?.params?.['id'] || !this.initialServer) {
          this.areas.closePanel();
          return;
        }
        this.tempServer = cloneDeep(this.initialServer);
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
    if (this.tempServer && this.initialServer) {
      return isDirty(this.tempServer, this.initialServer);
    }
  }

  public canSave(): boolean {
    return this.form.valid;
  }

  protected onSave() {
    this.doSave().subscribe(() => {
      this.reset();
    });
  }

  public doSave(): Observable<void> {
    return of(this.settings.editLdapServer(this.tempServer, this.initialServer));
  }

  private reset() {
    this.tempServer = this.initialServer;
    this.areas.closePanel();
  }
}
