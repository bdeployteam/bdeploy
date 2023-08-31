import {
  Component,
  OnDestroy,
  OnInit,
  TemplateRef,
  ViewChild,
  inject,
} from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { finalize, switchMap } from 'rxjs/operators';
import {
  BulkOperationResultDto,
  InstanceDto,
  ProductDto,
} from 'src/app/models/gen.dtos';
import {
  ACTION_CANCEL,
  ACTION_OK,
  BdDialogMessage,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { InstanceBulkService } from '../../services/instance-bulk.service';
import { ACTION_APPLY } from './../../../../core/components/bd-dialog-message/bd-dialog-message.component';

@Component({
  selector: 'app-bulk-manipulation',
  templateUrl: './bulk-manipulation.component.html',
})
export class BulkManipulationComponent implements OnInit, OnDestroy {
  protected starting$ = new BehaviorSubject<boolean>(false);
  protected stopping$ = new BehaviorSubject<boolean>(false);
  protected deleting$ = new BehaviorSubject<boolean>(false);
  protected installing$ = new BehaviorSubject<boolean>(false);
  protected activating$ = new BehaviorSubject<boolean>(false);
  protected updating$ = new BehaviorSubject<boolean>(false);

  private lock$ = combineLatest([
    this.starting$,
    this.stopping$,
    this.deleting$,
    this.installing$,
    this.activating$,
    this.updating$,
  ]).subscribe((a) => {
    const running = !a.every((e) => !e);
    this.bulk?.frozen$.next(running);
  });

  protected isAllSameProduct: boolean;
  protected selections: InstanceDto[];

  protected selectableProducts: ProductDto[];
  protected selectableProductLabels: string[];
  protected selectedTarget: ProductDto;

  protected bulkOpResult: BulkOperationResultDto;

  protected bulk = inject(InstanceBulkService);
  protected instance = inject(InstancesService);
  protected products = inject(ProductsService);

  private subscription: Subscription;
  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild('productChooser') private prodChooser: TemplateRef<any>;
  @ViewChild('opResult') private opResult: TemplateRef<any>;

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

  protected onStart() {
    this.starting$.next(true);
    this.bulk
      .start()
      .pipe(finalize(() => this.starting$.next(false)))
      .subscribe();
  }

  protected onStop() {
    this.stopping$.next(true);
    this.bulk
      .stop()
      .pipe(finalize(() => this.stopping$.next(false)))
      .subscribe();
  }

  protected onDelete() {
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
          .pipe(
            switchMap((r) => {
              this.bulkOpResult = r;
              return this.dialog.message({
                header: 'Result',
                template: this.opResult,
                actions: [ACTION_OK],
              });
            }),
            finalize(() => this.deleting$.next(false))
          )
          .subscribe();
      });
  }

  protected onInstall() {
    this.installing$.next(true);
    this.bulk
      .install()
      .pipe(
        switchMap((r) => {
          this.bulkOpResult = r;
          return this.dialog.message({
            header: 'Result',
            template: this.opResult,
            actions: [ACTION_OK],
          });
        }),
        finalize(() => this.installing$.next(false))
      )
      .subscribe();
  }

  protected onActivate() {
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
          .pipe(
            switchMap((r) => {
              this.bulkOpResult = r;
              return this.dialog.message({
                header: 'Result',
                template: this.opResult,
                actions: [ACTION_OK],
              });
            }),
            finalize(() => this.activating$.next(false))
          )
          .subscribe();
      });
  }

  protected onFetchStates() {
    this.bulk.fetchStates();
  }

  protected onUpdate() {
    this.updating$.next(true);

    // 1) figure out selectable products.
    const prod = this.selections[0].instanceConfiguration.product.name;
    this.selectableProducts = this.products.products$.value.filter(
      (v) => v.key.name === prod
    );
    this.selectableProductLabels = this.selectableProducts.map(
      (p) => p.key.tag
    );

    if (!this.selectableProducts?.length) {
      this.dialog
        .info(
          'No Target available',
          'There are no suitable target product versions available.',
          'warning'
        )
        .subscribe();
      return;
    }

    this.selectedTarget = this.selectableProducts[0];

    // 2) open dialog to select product.
    const msg: BdDialogMessage<boolean> = {
      header: 'Choose Target Product Version',
      template: this.prodChooser,
      validation: () => !!this.selectedTarget,
      actions: [ACTION_CANCEL, ACTION_APPLY],
    };
    this.dialog.message(msg).subscribe((r) => {
      if (!r) {
        this.updating$.next(false);
        return;
      }

      // 3) perform
      this.bulk
        .update(this.selectedTarget.key.tag)
        .pipe(
          switchMap((r) => {
            this.bulkOpResult = r;
            return this.dialog.message({
              header: 'Result',
              template: this.opResult,
              actions: [ACTION_OK],
            });
          }),
          finalize(() => this.updating$.next(false))
        )
        .subscribe();
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
