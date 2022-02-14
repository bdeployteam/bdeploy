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
  /* template */ pruning$ = new BehaviorSubject<boolean>(false);

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

  /* template */ doRepair(): void {
    this.dialog
      .confirm(
        'Repair',
        'Repairing will remove any (anyhow) damaged and unusable elements from the BHive'
      )
      .subscribe((confirmed) => {
        if (confirmed) {
          this.repairing$.next(true);
          this.hives
            .fsck(this.bhive$.value, true)
            .pipe(
              finalize(() => this.repairing$.next(false)),
              measure('Repairing ' + this.bhive$.value)
            )
            .subscribe((r) => {
              console.groupCollapsed('Damaged Objects');
              const keys = Object.keys(r);
              for (const key of keys) {
                console.log(key, ':', r[key]);
              }
              console.groupEnd();

              this.dialog
                .info(
                  `Repair`,
                  keys?.length
                    ? `Repair removed ${keys.length} damaged objects`
                    : `No damaged objects were found.`,
                  'build'
                )
                .subscribe();
            });
        }
      });
  }

  /* template */ doPrune(): void {
    this.pruning$.next(true);
    this.hives
      .prune(this.bhive$.value)
      .pipe(finalize(() => this.pruning$.next(false)))
      .subscribe((r) => {
        this.dialog
          .info(
            'Prune',
            `Prune freed <strong>${r}</strong> in ${this.bhive$.value}.`
          )
          .subscribe();
      });
  }
}
