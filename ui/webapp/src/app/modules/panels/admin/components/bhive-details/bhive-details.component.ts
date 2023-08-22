import { Component, OnDestroy, ViewChild } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { HiveService } from 'src/app/modules/primary/admin/services/hive.service';

@Component({
  selector: 'app-bhive-details',
  templateUrl: './bhive-details.component.html',
})
export class BhiveDetailsComponent implements OnDestroy {
  /* template */ bhive$ = new BehaviorSubject<string>(null);
  /* template */ repairing$ = new BehaviorSubject<boolean>(false);

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  private subscription: Subscription;

  constructor(areas: NavAreasService, private hives: HiveService) {
    this.subscription = areas.panelRoute$.subscribe((route) => {
      if (!route?.params || !route?.params['bhive']) {
        return;
      }

      this.bhive$.next(route.params['bhive']);
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ doRepairAndPrune(): void {
    this.dialog
      .confirm(
        'Repair and Prune',
        'Repairing will remove any (anyhow) damaged and unusable elements from the BHive'
      )
      .subscribe((confirmed) => {
        if (confirmed) {
          this.repairing$.next(true);
          this.hives
            .repairAndPrune(this.bhive$.value, true)
            .pipe(
              finalize(() => this.repairing$.next(false)),
              measure('Repairing and Pruning ' + this.bhive$.value)
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
              this.dialog
                .info(
                  `Repair and Prune`,
                  `${repairMessage}<br/>${pruneMessage}`,
                  'build'
                )
                .subscribe();
            });
        }
      });
  }
}
