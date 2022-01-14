import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { LDAPSettingsDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
  selector: 'edit-ldap-server',
  templateUrl: './edit-ldap-server.component.html',
  styleUrls: ['./edit-ldap-server.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditLdapServerComponent implements OnInit, OnDestroy {
  /* template */ tempServer: Partial<LDAPSettingsDto>;
  private subscription: Subscription;

  constructor(private settings: SettingsService, private areas: NavAreasService) {}

  ngOnInit(): void {
    this.subscription = this.settings.selectedServer$.subscribe((server) => {
      if (!server) {
        this.areas.closePanel();
        return;
      }
      this.tempServer = Object.assign({}, server);
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ onSave() {
    this.settings.editLdapServer(this.tempServer);
  }
}
