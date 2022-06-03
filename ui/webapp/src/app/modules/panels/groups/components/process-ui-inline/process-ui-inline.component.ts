import { Component, OnDestroy, ViewChild } from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { combineLatest, first, skipWhile, Subscription } from 'rxjs';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  ClientApp,
  ClientsService,
} from 'src/app/modules/primary/groups/services/clients.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';

@Component({
  selector: 'app-process-ui-inline',
  templateUrl: './process-ui-inline.component.html',
  styleUrls: ['./process-ui-inline.component.css'],
})
export class ProcessUiInlineComponent implements OnDestroy {
  /* template */ app: ClientApp;
  /* template */ url: SafeUrl;
  /* template */ directUri: string;
  /* template */ frameLoaded = false;
  /* template */ returnPanel: any[] = null;

  private rawUrl: string;
  private subscription: Subscription;

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(
    clients: ClientsService,
    cfg: ConfigService,
    nav: NavAreasService,
    groups: GroupsService,
    sanitizer: DomSanitizer
  ) {
    this.subscription = combineLatest([
      nav.panelRoute$,
      groups.current$,
      clients.apps$,
    ])
      .pipe(
        skipWhile(
          ([r, g, a]) =>
            !r?.params?.endpoint || !r?.params?.app || !g || !a?.length
        ),
        first() // only calculate this *ONCE* when all data is there.
      )
      .subscribe(([route, group, apps]) => {
        if (route.params.returnPanel) {
          let panel: string = route.params.returnPanel;
          if (panel.startsWith('/')) {
            panel = panel.substring(1);
          }
          this.returnPanel = panel.split('/');
        }

        this.app = apps.find(
          (a) =>
            a.endpoint?.uuid === route.params.app &&
            a.endpoint.endpoint.id === route.params.endpoint
        );

        if (!this.app) {
          return;
        }

        clients.getDirectUiURI(this.app).subscribe((url) => {
          this.directUri = url;
        });

        this.rawUrl = `${cfg.config.api}/master/upx/${group.name}/${
          this.app.instance.uuid
        }/${this.app.endpoint.uuid}/${
          this.app.endpoint.endpoint.id
        }${this.cpWithSlash(this.app.endpoint.endpoint.contextPath)}`;
        this.url = sanitizer.bypassSecurityTrustResourceUrl(this.rawUrl);
      });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ openUiEndpoint() {
    this.openUrl(this.rawUrl);
  }

  /* template */ openUiEndpointDirect() {
    this.openUrl(this.directUri);
  }

  private cpWithSlash(cp: string) {
    if (!cp) {
      return '/';
    }
    return cp[0] === '/' ? cp : `/${cp}`;
  }

  private openUrl(url: string) {
    window.open(url, '_blank', 'noreferrer,noopener');
  }
}
