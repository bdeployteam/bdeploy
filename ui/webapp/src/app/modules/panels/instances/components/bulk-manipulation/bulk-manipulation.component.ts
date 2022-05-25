import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { InstanceBulkService } from '../../services/instance-bulk.service';

@Component({
  selector: 'app-bulk-manipulation',
  templateUrl: './bulk-manipulation.component.html',
})
export class BulkManipulationComponent implements OnInit, OnDestroy {
  /* template */ starting$ = new BehaviorSubject<boolean>(false);
  /* template */ stopping$ = new BehaviorSubject<boolean>(false);
  /* template */ deleting$ = new BehaviorSubject<boolean>(false);
  /* template */ installing$ = new BehaviorSubject<boolean>(false);
  /* template */ activating$ = new BehaviorSubject<boolean>(false);
  /* template */ isAllSameProduct: boolean;
  /* template */ selections: InstanceDto[];
  private subscription: Subscription;
  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(
    public bulk: InstanceBulkService,
    public instance: InstancesService
  ) {}

  ngOnInit(): void {
    this.subscription = this.bulk.selection$.subscribe((selections) => {
      this.selections = selections;
      this.isAllSameProduct = selections.every(
        (i) =>
          !!i?.instanceConfiguration?.product?.name &&
          i.instanceConfiguration.product.name ===
            selections[0].instanceConfiguration.product.name
      );
    });
  }

  /* template */ onStart() {
    this.starting$.next(true);
    this.bulk
      .start()
      .pipe(finalize(() => this.starting$.next(false)))
      .subscribe();
  }

  /* template */ onStop() {
    this.stopping$.next(true);
    this.bulk
      .stop()
      .pipe(finalize(() => this.stopping$.next(false)))
      .subscribe();
  }

  /* template */ onDelete() {
    this.dialog
      .confirm(
        `Delete ${this.bulk.selection$.value.length} instances?`,
        `This will delete <strong>${this.bulk.selection$.value.length}</strong> instances. This action is irreversible. If you want to continue, confirm using <em>I UNDERSTAND</em>. Continue?`,
        'warning',
        'I UNDERSTAND',
        null
      )
      .subscribe((r) => {
        if (!r) {
          return;
        }

        this.deleting$.next(true);
        this.bulk
          .delete()
          .pipe(finalize(() => this.deleting$.next(false)))
          .subscribe();
      });
  }

  /* template */ onInstall() {
    this.installing$.next(true);
    this.bulk
      .install()
      .pipe(finalize(() => this.installing$.next(false)))
      .subscribe();
  }

  /* template */ onActivate() {
    this.dialog
      .confirm(
        'Activate',
        'This will activate the latest versions of each selected instance. Are you sure?',
        'warning',
        'I UNDERSTAND',
        'Confirm using I UNDERSTAND'
      )
      .subscribe((r) => {
        if (!r) return;

        this.activating$.next(true);
        this.bulk
          .activate()
          .pipe(finalize(() => this.activating$.next(false)))
          .subscribe();
      });
  }

  /* template */ onFetchStates() {
    this.bulk.fetchStates();
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
