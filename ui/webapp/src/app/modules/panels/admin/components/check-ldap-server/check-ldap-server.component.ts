import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Subject, Subscription, combineLatest } from 'rxjs';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'check-ldap-server',
  templateUrl: './check-ldap-server.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckLdapServerComponent implements OnInit, OnDestroy {
  private settings = inject(SettingsService);
  private auth = inject(AuthAdminService);
  private areas = inject(NavAreasService);

  protected tempServer: Partial<LDAPSettingsDto>;

  private subscription: Subscription;
  protected checkResult$ = new Subject<string>();

  ngOnInit(): void {
    this.subscription = combineLatest([this.areas.panelRoute$, this.settings.settings$]).subscribe(
      ([route, settings]) => {
        if (!settings || !route?.params || !route.params['id']) {
          this.areas.closePanel();
          return;
        }
        const server = settings.auth.ldapSettings.find((a) => a.id === route.params['id']);
        this.checkResult$.next(`Checking ...`);
        this.auth.testLdapServer(server).subscribe((r) => {
          this.checkResult$.next(r);
        });
      },
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
