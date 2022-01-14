import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { AuthAdminService } from 'src/app/modules/primary/admin/services/auth-admin.service';

@Component({
  selector: 'check-ldap-server',
  templateUrl: './check-ldap-server.component.html',
  styleUrls: ['./check-ldap-server.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckLdapServerComponent implements OnInit {
  /* template */ tempServer: Partial<LDAPSettingsDto>;

  private subscription: Subscription;
  /* template */ checkResult$ = new Subject<string>();

  constructor(private settings: SettingsService, private auth: AuthAdminService, private areas: NavAreasService) {}

  ngOnInit(): void {
    this.subscription = this.settings.selectedServer$.subscribe((server) => {
      if (!server) {
        this.areas.closePanel();
        return;
      }
      this.checkResult$.next(`Checking ...`);
      this.auth.testLdapServer(server).subscribe((r) => {
        this.checkResult$.next(r);
      });
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
