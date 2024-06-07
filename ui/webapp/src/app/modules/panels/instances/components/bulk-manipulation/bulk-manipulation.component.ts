import { Component, OnDestroy, OnInit, TemplateRef, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { finalize, map, switchMap } from 'rxjs/operators';
import { Actions, BulkOperationResultDto, InstanceDto, ProductDto } from 'src/app/models/gen.dtos';
import {
  ACTION_CANCEL,
  ACTION_OK,
  BdDialogMessage,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { InstanceBulkService } from '../../services/instance-bulk.service';
import { ACTION_APPLY } from './../../../../core/components/bd-dialog-message/bd-dialog-message.component';

@Component({
  selector: 'app-bulk-manipulation',
  templateUrl: './bulk-manipulation.component.html',
})
export class BulkManipulationComponent implements OnInit, OnDestroy {
  protected bulk = inject(InstanceBulkService);
  protected instance = inject(InstancesService);
  protected products = inject(ProductsService);
  protected actions = inject(ActionsService);

  private starting$ = new BehaviorSubject<boolean>(false);
  private restarting$ = new BehaviorSubject<boolean>(false);
  private stopping$ = new BehaviorSubject<boolean>(false);
  private deleting$ = new BehaviorSubject<boolean>(false);
  private installing$ = new BehaviorSubject<boolean>(false);
  private activating$ = new BehaviorSubject<boolean>(false);
  private updating$ = new BehaviorSubject<boolean>(false);

  protected isAllSameProduct: boolean;
  protected selectableProducts: ProductDto[];
  protected selectableProductLabels: string[];
  protected selectedTarget: ProductDto;
  protected bulkOpResult: BulkOperationResultDto;

  private ids$ = this.bulk.selection$.pipe(map((i) => i.map((x) => x.instanceConfiguration.id)));

  protected mappedStart$ = this.actions.action([Actions.START_INSTANCE], this.starting$, null, this.ids$);
  protected mappedRestart$ = this.actions.action(
    [Actions.START_INSTANCE, Actions.STOP_INSTANCE],
    this.restarting$,
    null,
    this.ids$,
  );
  protected mappedStop$ = this.actions.action([Actions.STOP_INSTANCE], this.stopping$, null, this.ids$);
  protected mappedDelete$ = this.actions.action([Actions.DELETE_INSTANCE], this.deleting$, null, this.ids$);
  protected mappedInstall$ = this.actions.action([Actions.INSTALL], this.installing$, null, this.ids$);
  protected mappedActivate$ = this.actions.action([Actions.ACTIVATE], this.activating$, null, this.ids$);
  protected mappedUpdate$ = this.actions.action([Actions.UPDATE_PRODUCT_VERSION], this.updating$, null, this.ids$);

  private subscription: Subscription;
  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild('productChooser') private prodChooser: TemplateRef<any>;
  @ViewChild('opResult') private opResult: TemplateRef<any>;

  ngOnInit(): void {
    this.subscription = this.bulk.selection$.subscribe((selections) => {
      this.isAllSameProduct = selections.every(
        (i) =>
          !!i?.instanceConfiguration?.product?.name &&
          i.instanceConfiguration.product.name === selections[0].instanceConfiguration.product.name,
      );
    });

    this.subscription.add(
      combineLatest([
        this.starting$,
        this.stopping$,
        this.deleting$,
        this.installing$,
        this.activating$,
        this.updating$,
      ]).subscribe((a) => {
        const running = !a.every((e) => !e);
        this.bulk?.frozen$.next(running);
      }),
    );
  }

  protected onStart() {
    this.starting$.next(true);
    this.bulk
      .start()
      .pipe(
        switchMap((resultDto) => {
          this.bulkOpResult = resultDto;
          return this.dialog.message({
            header: 'Result',
            template: this.opResult,
            actions: [ACTION_OK],
          });
        }),
        finalize(() => this.starting$.next(false)),
      )
      .subscribe();
  }

  protected onRestart() {
    this.restarting$.next(true);
    this.bulk
      .restart()
      .pipe(
        switchMap((resultDto) => {
          this.bulkOpResult = resultDto;
          return this.dialog.message({
            header: 'Result',
            template: this.opResult,
            actions: [ACTION_OK],
          });
        }),
        finalize(() => this.restarting$.next(false)),
      )
      .subscribe();
  }

  protected onStop() {
    this.stopping$.next(true);
    this.bulk
      .stop()
      .pipe(
        switchMap((resultDto) => {
          this.bulkOpResult = resultDto;
          return this.dialog.message({
            header: 'Result',
            template: this.opResult,
            actions: [ACTION_OK],
          });
        }),
        finalize(() => this.stopping$.next(false)),
      )
      .subscribe();
  }

  protected onDelete() {
    this.dialog
      .confirm(
        `Delete ${this.bulk.selection$.value.length} instances?`,
        `This will delete <strong>${this.bulk.selection$.value.length}</strong> instances. This action is irreversible. If you want to continue, confirm using <em>I UNDERSTAND</em>. Continue?`,
        'warning',
        'I UNDERSTAND',
        null,
      )
      .subscribe((r) => {
        if (!r) {
          return;
        }

        this.deleting$.next(true);
        this.bulk
          .delete()
          .pipe(
            switchMap((resultDto) => {
              this.bulkOpResult = resultDto;
              return this.dialog.message({
                header: 'Result',
                template: this.opResult,
                actions: [ACTION_OK],
              });
            }),
            finalize(() => this.deleting$.next(false)),
          )
          .subscribe();
      });
  }

  protected onInstall() {
    this.installing$.next(true);
    this.bulk
      .install()
      .pipe(
        switchMap((resultDto) => {
          this.bulkOpResult = resultDto;
          return this.dialog.message({
            header: 'Result',
            template: this.opResult,
            actions: [ACTION_OK],
          });
        }),
        finalize(() => this.installing$.next(false)),
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
        'Confirm using I UNDERSTAND',
      )
      .subscribe((r) => {
        if (!r) return;

        this.activating$.next(true);
        this.bulk
          .activate()
          .pipe(
            switchMap((resultDto) => {
              this.bulkOpResult = resultDto;
              return this.dialog.message({
                header: 'Result',
                template: this.opResult,
                actions: [ACTION_OK],
              });
            }),
            finalize(() => this.activating$.next(false)),
          )
          .subscribe();
      });
  }

  protected onFetchStates() {
    this.bulk.fetchStates();
  }

  protected countRestrictions(sel: InstanceDto[]): number {
    return sel.filter((d) => d.instanceConfiguration.productFilterRegex?.length)?.length;
  }

  protected onUpdate() {
    this.updating$.next(true);

    // 1) figure out selectable products.
    const prod = this.bulk.selection$.value[0].instanceConfiguration.product.name;
    this.selectableProducts = this.products.products$.value.filter((v) => v.key.name === prod);
    this.selectableProductLabels = this.selectableProducts.map((p) => p.key.tag);

    if (!this.selectableProducts?.length) {
      this.dialog
        .info('No Target available', 'There are no suitable target product versions available.', 'warning')
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
          switchMap((resultDto) => {
            this.bulkOpResult = resultDto;
            return this.dialog.message({
              header: 'Result',
              template: this.opResult,
              actions: [ACTION_OK],
            });
          }),
          finalize(() => this.updating$.next(false)),
        )
        .subscribe();
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
