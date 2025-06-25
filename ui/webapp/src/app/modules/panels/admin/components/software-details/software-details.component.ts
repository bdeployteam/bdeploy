import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { Actions, OperatingSystem } from 'src/app/models/gen.dtos';
import {
  BdDialogToolbarComponent
} from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { SoftwareUpdateService, SoftwareVersion } from 'src/app/modules/primary/admin/services/software-update.service';


import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-software-details',
    templateUrl: './software-details.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdButtonComponent, AsyncPipe]
})
export class SoftwareDetailsComponent implements OnInit, OnDestroy {
  private readonly software = inject(SoftwareUpdateService);
  private readonly actions = inject(ActionsService);
  private readonly areas = inject(NavAreasService);
  protected readonly cfg = inject(ConfigService);

  private readonly deleting$ = new BehaviorSubject<boolean>(false);
  private readonly installing$ = new BehaviorSubject<boolean>(false);

  protected software$ = new BehaviorSubject<SoftwareVersion>(null);
  protected mappedDelete$ = this.actions.action(
    [Actions.DELETE_UPDATES],
    this.deleting$,
    null,
    null,
    this.software$.pipe(map((s) => s?.version)),
  );
  protected mappedInstall$ = this.actions.action([Actions.UPDATE, Actions.RESTART_SERVER], this.installing$); // global!

  protected systemOs$ = new BehaviorSubject<OperatingSystem[]>(null);
  protected launcherOs$ = new BehaviorSubject<OperatingSystem[]>(null);

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = combineLatest([this.areas.panelRoute$, this.software.software$]).subscribe(([r, s]) => {
      if (!r?.params?.['version'] || !s) return;

      const version = r.params['version'];
      const sw = s.find((x) => x.version === version);
      this.software$.next(sw);

      this.systemOs$.next(sw.system.map((x) => getAppOs(x)).filter(o => !!o));
      this.launcherOs$.next(sw.launcher.map((x) => getAppOs(x)).filter(o => !!o));
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected doDelete() {
    this.dialog
      .confirm(
        'Delete Version',
        `This will delete all associated system and launcher versions for each operating system.`,
        'delete',
      )
      .subscribe((r) => {
        if (!r) return;

        this.deleting$.next(true);
        this.software
          .deleteVersion([...this.software$.value.system, ...this.software$.value.launcher])
          .pipe(finalize(() => this.deleting$.next(false)))
          .subscribe(() => {
            this.tb.closePanel();
            this.software.load();
          });
      });
  }

  protected doInstall() {
    this.dialog
      .confirm(
        'Install Version',
        `Installing this version will cause a short downtime, typically a few seconds.`,
        'system_update',
      )
      .subscribe((r) => {
        if (!r) return;

        this.installing$.next(true);
        this.software
          .updateBdeploy(this.software$.value.system)
          .pipe(finalize(() => this.installing$.next(false)))
          .subscribe(() => {
            this.cfg.isUpdateInstallSucceeded$.next(true);
            this.software.load();
          });
      });
  }
}
