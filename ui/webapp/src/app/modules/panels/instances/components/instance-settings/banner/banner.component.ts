import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { cloneDeep, isEqual } from 'lodash-es';
import { BehaviorSubject, of, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { InstanceBannerRecord } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

@Component({
  selector: 'app-banner',
  templateUrl: './banner.component.html',
  styleUrls: ['./banner.component.css'],
})
export class BannerComponent implements OnInit, OnDestroy, DirtyableDialog {
  private readonly DEFAULT_BANNER = {
    text: 'Banner information goes here.',
    user: this.auth.getUsername(),
    foregroundColor: '#000000',
    backgroundColor: '#ffffc8',
    timestamp: Date.now(),
  };

  /* template */ saving$ = new BehaviorSubject<boolean>(false);
  /* template */ removing$ = new BehaviorSubject<boolean>(false);
  /* template */ banner: InstanceBannerRecord = this.DEFAULT_BANNER;
  /* template */ orig: InstanceBannerRecord = cloneDeep(this.banner);

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  constructor(public servers: ServersService, public instances: InstancesService, private auth: AuthenticationService, areas: NavAreasService) {
    this.subscription = this.instances.current$.subscribe((s) => {
      let confirm = of(true);
      if (this.isDirty()) {
        if (!!s && !isEqual(this.orig, s.banner)) {
          // original banner changed *elsewhere*, *and* we have changes, confirm reset
          confirm = this.dialog.confirm(
            'Banner Changed',
            'The banner has been changed in another session. You can update to those changes but will loose local modifications.',
            'merge_type'
          );
        }
      }

      confirm.subscribe((r) => {
        if (r) {
          if (!!s?.banner?.text) {
            this.banner = s.banner;
          } else {
            this.banner = this.DEFAULT_BANNER;
          }
          this.orig = cloneDeep(this.banner);
        }
      });
    });

    this.subscription.add(areas.registerDirtyable(this, 'panel'));
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  isDirty(): boolean {
    return !!this.orig && !isEqual(this.banner, this.orig);
  }

  /* template */ doApply() {
    this.saving$.next(true);
    this.orig = null; // make sure we're no longer dirty.
    this.instances
      .updateBanner(this.banner)
      .pipe(finalize(() => this.saving$.next(false)))
      .subscribe((_) => {
        this.tb.closePanel();
      });
  }

  /* template */ doRemove() {
    this.removing$.next(true);
    this.orig = null; // make sure we're no longer dirty.
    this.banner.text = null; // backend hooks on text != null.
    this.instances
      .updateBanner(this.banner)
      .pipe(finalize(() => this.removing$.next(false)))
      .subscribe();
  }
}
