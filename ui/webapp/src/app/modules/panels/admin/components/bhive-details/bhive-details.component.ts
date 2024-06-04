import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { Actions, HiveInfoDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { HiveService } from 'src/app/modules/primary/admin/services/hive.service';

@Component({
  selector: 'app-bhive-details',
  templateUrl: './bhive-details.component.html',
})
export class BhiveDetailsComponent implements OnInit, OnDestroy {
  private areas = inject(NavAreasService);
  private hives = inject(HiveService);

  protected bhive$ = new BehaviorSubject<string>(null);
  protected details$ = new BehaviorSubject<HiveInfoDto>(null);

  private repairing$ = new BehaviorSubject<boolean>(false);
  private enablingPool$ = new BehaviorSubject<boolean>(false);
  private disablingPool$ = new BehaviorSubject<boolean>(false);

  private actions = inject(ActionsService);
  protected mappedRepair$ = this.actions.action(
    [Actions.FSCK_BHIVE, Actions.PRUNE_BHIVE],
    this.repairing$,
    this.bhive$,
  );
  protected mappedEnablePool$ = this.actions.action([Actions.ENABLE_POOL], this.enablingPool$, this.bhive$);
  protected mappedDisablePool$ = this.actions.action([Actions.DISABLE_POOL], this.disablingPool$, this.bhive$);

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = this.areas.panelRoute$.subscribe((route) => {
      if (!route?.params?.['bhive']) {
        return;
      }

      this.bhive$.next(route.params['bhive']);

      this.hives.hives$.subscribe((list) => {
        this.details$.next(list.find((h) => h.name === this.bhive$.value));
      });
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected doRepairAndPrune(): void {
    this.dialog
      .confirm('Repair and Prune', 'Repairing will remove any (anyhow) damaged and unusable elements from the BHive')
      .subscribe((confirmed) => {
        if (confirmed) {
          this.repairing$.next(true);
          this.hives
            .repairAndPrune(this.bhive$.value, true)
            .pipe(
              finalize(() => this.repairing$.next(false)),
              measure('Repairing and Pruning ' + this.bhive$.value),
            )
            .subscribe(({ repaired, pruned }) => {
              console.groupCollapsed('Damaged Objects');
              const keys = Object.keys(repaired);
              for (const key of keys) {
                console.log(key, ':', repaired[key]);
              }
              console.groupEnd();

              const repairMessage = keys?.length
                ? `Repair removed ${keys.length} damaged objects.`
                : `No damaged objects were found.`;
              const pruneMessage = `Prune freed <strong>${pruned}</strong> in ${this.bhive$.value}.`;
              this.dialog.info(`Repair and Prune`, `${repairMessage}<br/>${pruneMessage}`, 'build').subscribe();
            });
        }
      });
  }

  protected doEnablePool() {
    this.enablingPool$.next(true);
    this.hives
      .enablePool(this.bhive$.value)
      .pipe(finalize(() => this.enablingPool$.next(false)))
      .subscribe();
  }

  protected doDisablePool() {
    this.disablingPool$.next(true);
    this.hives
      .disablePool(this.bhive$.value)
      .pipe(finalize(() => this.disablingPool$.next(false)))
      .subscribe();
  }
}
