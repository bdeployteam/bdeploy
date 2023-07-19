import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { Subject, Subscription, combineLatest } from 'rxjs';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';

@Component({
  selector: 'app-import-ldap-server',
  templateUrl: './import-ldap-server.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImportLdapServerComponent implements OnInit, OnDestroy {
  /* template */ tempServer: Partial<LDAPSettingsDto>;

  private subscription: Subscription;
  /* template */ checkResult$ = new Subject<string>();

  constructor(
    private settings: SettingsService,
    private auth: AuthAdminService,
    private areas: NavAreasService
  ) {}

  ngOnInit(): void {
    this.subscription = combineLatest([
      this.areas.panelRoute$,
      this.settings.settings$,
    ]).subscribe(([route, settings]) => {
      if (!settings || !route?.params || !route.params['id']) {
        this.areas.closePanel();
        return;
      }
      const server = settings.auth.ldapSettings.find(
        (a) => a.id === route.params['id']
      );
      this.checkResult$.next(`Checking ...`);
      this.auth.importAccountsLdapServer(server).subscribe((r) => {
        this.checkResult$.next(r);
      });
    });
  }

  ngOnDestroy(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }
}
