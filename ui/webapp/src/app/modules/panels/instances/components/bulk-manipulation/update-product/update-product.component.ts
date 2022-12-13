import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, of } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceUpdateDto, ProductDto } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ProductsColumnsService } from 'src/app/modules/primary/products/services/products-columns.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { InstanceBulkService } from '../../../services/instance-bulk.service';

@Component({
  selector: 'app-update-product',
  templateUrl: './update-product.component.html',
})
export class UpdateProductComponent implements OnInit, OnDestroy {
  private readonly productUpdateAction: BdDataColumn<ProductDto> = {
    id: 'update',
    name: 'Upd.',
    data: () => 'Set as current version for all selected instances.',
    action: (r) => this.updateProduct(r),
    icon: () => 'security_update_good',
    width: '40px',
  };

  /* template */ products$ = new BehaviorSubject<ProductDto[]>(null);
  /* template */ processing$ = new BehaviorSubject<boolean>(false);
  /* template */ columns: BdDataColumn<ProductDto>[] = [
    this.productCols.productVersionColumn,
    this.productUpdateAction,
  ];
  /* template */ saved$ = new BehaviorSubject<number>(0);

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  constructor(
    public bulk: InstanceBulkService,
    public products: ProductsService,
    private productCols: ProductsColumnsService
  ) {}

  ngOnInit(): void {
    // need to do this on *next* update cycle.
    setTimeout(() => this.bulk.frozen$.next(true));

    if (!this.bulk.selection$.value) {
      return;
    }

    const prodName =
      this.bulk.selection$.value[0].instanceConfiguration.product.name;
    this.products.products$.subscribe((p) => {
      if (!p) {
        return;
      }

      this.products$.next(p.filter((x) => x.key.name === prodName));
    });
  }

  ngOnDestroy(): void {
    this.bulk.frozen$.next(false);
  }

  private updateProduct(product: ProductDto): void {
    this.saved$.next(0);
    this.processing$.next(true);

    const updates: InstanceUpdateDto[] = [];
    const errors = [];
    const instances = this.bulk.selection$.value;

    this.bulk.prepareUpdate(product, instances).subscribe({
      next: (u) => {
        updates.push(u);
      },
      error: (e) => {
        errors.push(e);
      },
      complete: () => {
        if (errors.length > 0) {
          // display generic internal error and stay on the page with the current selection - nothing happened.
          this.dialog
            .info(
              'Sorry that did not work',
              'There was an unexpected error during server communication',
              'error'
            )
            .pipe(finalize(() => this.processing$.next(false)))
            .subscribe();
        } else {
          const problematic = updates.filter((p) => !!p.validation?.length);
          if (problematic.length) {
            // display brief validation summary and stay on the page with the current selection - nothing happened.
            this.dialog
              .info(
                'Validation Issues',
                `<p>Unfortunately there are validation issues, the bulk update cannot be performed.</p><mat-divider></mat-divider><ul class="list-disc list-inside">${problematic
                  .map(
                    (p) =>
                      `<li>${p.config.config.name}: ${
                        p.validation.length
                      } issue${p.validation.length > 1 ? 's' : ''}</li>`
                  )
                  .join()}</ul><p>No changes were performed</p>`,
                'warning'
              )
              .pipe(finalize(() => this.processing$.next(false)))
              .subscribe();
          } else {
            // all is well, we can now save the updates.
            this.bulk
              .saveUpdate(updates, instances)
              .pipe(
                finalize(() => {
                  this.processing$.next(false);

                  // in case of errors - DISPLAY them :)
                  let msg = of(true);
                  if (errors?.length) {
                    msg = this.dialog.info(
                      `Errors while saving`,
                      `<strong>${errors.length}</strong> errors have occured during saving. Please verify the correctness of all affected instances.`,
                      'warning'
                    );
                  }

                  msg.subscribe(() => this.tb.closePanel());
                })
              )
              .subscribe({
                next: () => {
                  this.saved$.next(this.saved$.value + 1);
                },
                error: (e) => {
                  errors.push(e);
                },
              });
          }
        }
      },
    });
  }
}
