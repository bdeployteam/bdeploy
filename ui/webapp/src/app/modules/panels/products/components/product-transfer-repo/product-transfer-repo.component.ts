import { StepperSelectionEvent } from '@angular/cdk/stepper';
import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { MatStepper, MatStep } from '@angular/material/stepper';
import { groupBy } from 'lodash-es';
import { BehaviorSubject, combineLatest, finalize, map } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { Actions, ProductDto, SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ProductsColumnsService } from 'src/app/modules/primary/products/services/products-columns.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { RepositoryService } from 'src/app/modules/primary/repositories/services/repository.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFormSelectComponent } from '../../../../core/components/bd-form-select/bd-form-select.component';
import { FormsModule } from '@angular/forms';
import { BdLoadingOverlayComponent } from '../../../../core/components/bd-loading-overlay/bd-loading-overlay.component';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
  selector: 'app-product-transfer-repo',
  templateUrl: './product-transfer-repo.component.html',
  imports: [
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    MatStepper,
    MatStep,
    BdFormSelectComponent,
    FormsModule,
    BdLoadingOverlayComponent,
    BdDataTableComponent,
    BdButtonComponent,
    AsyncPipe,
  ],
})
export class ProductTransferRepoComponent implements OnInit {
  private readonly repositories = inject(RepositoriesService);
  private readonly repository = inject(RepositoryService);
  private readonly products = inject(ProductsService);
  private readonly prodCols = inject(ProductsColumnsService);
  private readonly areas = inject(NavAreasService);
  private readonly actions = inject(ActionsService);

  protected readonly prodVersionColumns: BdDataColumn<ProductDto, unknown>[] = [this.prodCols.productVersionColumn];

  protected repos: SoftwareRepositoryConfiguration[];
  protected repoLabels: string[];
  protected selectedRepo: SoftwareRepositoryConfiguration;
  protected selectedProductId: string;
  protected productsLoading$ = new BehaviorSubject<boolean>(false);
  protected prodsById: Record<string, ProductDto[]> = {};
  protected prodIds: string[];
  protected prodLabels: string[];
  protected selectedVersions$ = new BehaviorSubject<ProductDto[]>([]);

  protected loading$ = combineLatest([this.products.loading$, this.repositories.loading$]).pipe(
    map(([a, b]) => a || b)
  );

  protected importing$ = new BehaviorSubject<boolean>(false);
  protected mappedImporting$ = this.actions.action(
    [Actions.IMPORT_PROD_REPO],
    this.importing$,
    null,
    null,
    this.selectedVersions$.pipe(map((x) => x.map((y) => `${y.key.name}:${y.key.tag}`)))
  );

  private queryRepo: string = null;
  private queryProduct: string = null;

  @ViewChild(MatStepper) stepper: MatStepper;

  ngOnInit(): void {
    const snap = this.areas.panelRoute$.value;
    this.queryProduct = snap.queryParamMap.get('product');
    this.queryRepo = snap.queryParamMap.get('repo');

    this.repositories.repositories$.subscribe((repos) => {
      this.repos = repos;
      this.repoLabels = repos.map((r) => r.name);
      this.preselectRepo();
    });
  }

  private preselectRepo(): void {
    if (!this.queryRepo) return;
    this.selectedRepo = this.repos.find((repo) => repo.name === this.queryRepo);
    this.queryRepo = null;
    if (!this.selectedRepo) return;
    setTimeout(() => {
      this.stepper.selected.completed = true;
      this.stepper.next();
    });
  }

  private preselectProduct(products: ProductDto[]): void {
    if (!this.queryProduct) return;
    this.selectedProductId = products.find((p) => !!this.queryProduct && p.key.name === this.queryProduct)?.product;
    this.queryProduct = null;
    if (!this.selectedProductId) return;
    this.stepper.selected.completed = true;
    this.stepper.next();
  }

  protected onStepSelectionChange(event: StepperSelectionEvent) {
    switch (event.selectedIndex) {
      case 0:
        this.selectedRepo = null;
        this.selectedProductId = null;
        break;
      case 1:
        this.selectedProductId = null;
        this.productsLoading$.next(true);
        this.repository
          .loadProductsOf(this.selectedRepo.name)
          .pipe(finalize(() => this.productsLoading$.next(false)))
          .subscribe((prods) => {
            const products = this.products.products$.value || [];
            const filtered = prods.filter(
              (p) => !products.some((p2) => p2.key.name === p.key.name && p2.key.tag === p.key.tag)
            );
            this.prodsById = groupBy(filtered, (p) => p.product);
            this.prodIds = Object.keys(this.prodsById);
            this.prodLabels = this.prodIds.map((k) => this.prodsById[k][0].name); // first is the one with the highest version as well
            this.preselectProduct(filtered);
          });
        break;
    }
  }

  protected importVersions() {
    if (!this.selectedVersions$.value?.length) {
      this.areas.closePanel();
      return;
    }

    this.importing$.next(true);
    this.products
      .importProduct(this.selectedVersions$.value, this.selectedRepo.name)
      .pipe(finalize(() => this.importing$.next(false)))
      .subscribe(() => this.areas.closePanel());
  }
}
