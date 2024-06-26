import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { cloneDeep, isEqual } from 'lodash-es';
import { BehaviorSubject, Observable, Subscription, of } from 'rxjs';
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
})
export class BannerComponent implements OnInit, OnDestroy, AfterViewInit, DirtyableDialog {
  private auth = inject(AuthenticationService);
  private areas = inject(NavAreasService);
  protected servers = inject(ServersService);
  protected instances = inject(InstancesService);

  private readonly DEFAULT_BANNER = {
    text: 'Banner information goes here.',
    user: this.auth.getCurrentUsername(),
    foregroundColor: '#000000',
    backgroundColor: '#ffffff',
    timestamp: Date.now(), // local time ok.
  };

  protected saving$ = new BehaviorSubject<boolean>(false);
  protected removing$ = new BehaviorSubject<boolean>(false);
  protected banner: InstanceBannerRecord = this.DEFAULT_BANNER;
  protected orig: InstanceBannerRecord = cloneDeep(this.banner);
  protected disableApply = true;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChild(ColorSelectGroupComponent)
  private colorSelect: ColorSelectGroupComponent;

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
  }

  ngAfterViewInit(): void {
    this.subscription.add(
      this.instances.current$.subscribe((s) => {
        let confirm = of(true);
        if (this.isDirty() && !!s && !isEqual(this.orig, s.banner)) {
          // original banner changed *elsewhere*, *and* we have changes, confirm reset
          confirm = this.dialog.confirm(
            'Banner Changed',
            'The banner has been changed in another session. You can update to those changes but will loose local modifications.',
            'merge_type',
          );
        }

        confirm.subscribe((r) => {
          if (r) {
            if (s?.banner?.text) {
              this.banner = s.banner;

              // this tries to find a matching preset, which will not work if the banner has been
              // set prior to 4.0, which only has presets allowed, no freely defined colors.
              this.colorSelect.trySetSelected(s.banner.foregroundColor, s.banner.backgroundColor);
            } else {
              this.banner = this.DEFAULT_BANNER;
              this.colorSelect.setDefault();
            }
            this.orig = cloneDeep(this.banner);
            this.disableApply = this.isDirty();
          }
        });
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return !!this.orig && !isEqual(this.banner, this.orig);
  }

  protected onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public doSave(): Observable<unknown> {
    this.saving$.next(true);
    this.orig = null; // make sure we're no longer dirty.
    return this.instances.updateBanner(this.banner).pipe(finalize(() => this.saving$.next(false)));
  }

  protected doRemove() {
    this.removing$.next(true);
    this.orig = null; // make sure we're no longer dirty.
    this.banner.text = null; // backend hooks on text != null.
    this.instances
      .updateBanner(this.banner)
      .pipe(finalize(() => this.removing$.next(false)))
      .subscribe();
  }

  protected doChangeColor(sel: ColorDef) {
    this.banner.foregroundColor = sel.fg;
    this.banner.backgroundColor = sel.bg;
    this.disableApply = this.isDirty();
  }

  protected onChange() {
    this.disableApply = this.isDirty();
  }
}
