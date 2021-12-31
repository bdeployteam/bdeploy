import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { cloneDeep, isEqual } from 'lodash-es';
import { BehaviorSubject, Observable, of, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { InstanceBannerRecord } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ColorDef, ColorSelectGroupComponent } from './color-select-group/color-select-group.component';

@Component({
  selector: 'app-banner',
  templateUrl: './banner.component.html',
  styleUrls: ['./banner.component.css'],
})
export class BannerComponent implements OnInit, OnDestroy, AfterViewInit, DirtyableDialog {
  private readonly DEFAULT_BANNER = {
    text: 'Banner information goes here.',
    user: this.auth.getUsername(),
    foregroundColor: '#000000',
    backgroundColor: '#ffffff',
    timestamp: Date.now(), // local time ok.
  };

  /* template */ saving$ = new BehaviorSubject<boolean>(false);
  /* template */ removing$ = new BehaviorSubject<boolean>(false);
  /* template */ banner: InstanceBannerRecord = this.DEFAULT_BANNER;
  /* template */ orig: InstanceBannerRecord = cloneDeep(this.banner);
  /* template */ disableApply: boolean;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChild(ColorSelectGroupComponent) private colorSelect: ColorSelectGroupComponent;

  private subscription: Subscription;

  constructor(public servers: ServersService, public instances: InstancesService, private auth: AuthenticationService, areas: NavAreasService) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnInit(): void {
    this.disableApply = this.isDirty();
  }

  ngAfterViewInit(): void {
    this.subscription.add(
      this.instances.current$.subscribe((s) => {
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

              // this tries to find a matching preset, which will not work if the banner has been
              // set prior to 4.0, which only has presets allowed, no freely defined colors.
              this.colorSelect.trySetSelected(s.banner.foregroundColor, s.banner.backgroundColor);
            } else {
              this.banner = this.DEFAULT_BANNER;
              this.colorSelect.setDefault();
            }
            this.orig = cloneDeep(this.banner);
          }
        });
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public isDirty(): boolean {
    return !!this.orig && !isEqual(this.banner, this.orig);
  }

  /* template */ onSave() {
    this.doSave().subscribe((_) => this.tb.closePanel());
  }

  public doSave(): Observable<any> {
    this.saving$.next(true);
    this.orig = null; // make sure we're no longer dirty.
    return this.instances.updateBanner(this.banner).pipe(finalize(() => this.saving$.next(false)));
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

  /* template */ doChangeColor(sel: ColorDef) {
    this.banner.foregroundColor = sel.fg;
    this.banner.backgroundColor = sel.bg;
    this.disableApply = this.isDirty();
  }

  /* template */ onChange() {
    this.disableApply = this.isDirty();
  }
}
