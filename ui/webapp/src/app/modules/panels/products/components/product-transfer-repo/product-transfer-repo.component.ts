import { StepperSelectionEvent } from '@angular/cdk/stepper';
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatStepper } from '@angular/material/stepper';
import { groupBy } from 'lodash-es';
import { BehaviorSubject, combineLatest, finalize, map } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import {
  ProductDto,
  SoftwareRepositoryConfiguration,
} from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ProductsColumnsService } from 'src/app/modules/primary/products/services/products-columns.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { RepositoryService } from 'src/app/modules/primary/repositories/services/repository.service';

@Component({
  selector: 'app-product-transfer-repo',
  templateUrl: './product-transfer-repo.component.html',
})
export class ProductTransferRepoComponent implements OnInit {
  /* template */ prodVersionColumns: BdDataColumn<ProductDto>[] = [
    this.prodCols.productVersionColumn,
  ];

  /* template */ repos: SoftwareRepositoryConfiguration[];
  /* template */ repoLabels: string[];
  /* template */ selectedRepo: SoftwareRepositoryConfiguration;
  /* template */ selectedProductId: string;
  /* template */ productsLoading$ = new BehaviorSubject<boolean>(false);
  /* template */ allProducts = [];
  /* template */ prodsById: { [key: string]: ProductDto[] } = {};
  /* template */ prodIds: string[];
  /* template */ prodLabels: string[];
  /* template */ selectedVersions: ProductDto[];

  /* template */ loading$ = combineLatest([
    this.products.loading$,
    this.repositories.loading$,
  ]).pipe(map(([a, b]) => a || b));
  /* template */ importing$ = new BehaviorSubject<boolean>(false);

  private queryRepo: string = null;
  private queryProduct: string = null;

  @ViewChild(MatStepper) stepper: MatStepper;

  constructor(
    private repositories: RepositoriesService,
    private repository: RepositoryService,
    private products: ProductsService,
    private prodCols: ProductsColumnsService,
    private areas: NavAreasService
  ) {}

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
    this.selectedProductId = products.find(
      (p) => !!this.queryProduct && p.key.name === this.queryProduct
    )?.product;
    this.queryProduct = null;
    if (!this.selectedProductId) return;
    this.stepper.selected.completed = true;
    this.stepper.next();
  }

  /* template */ onStepSelectionChange(event: StepperSelectionEvent) {
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
              (p) =>
                !products.find(
                  (p2) => p2.key.name === p.key.name && p2.key.tag === p.key.tag
                )
            );
            this.prodsById = groupBy(filtered, (p) => p.product);
            this.prodIds = Object.keys(this.prodsById);
            this.prodLabels = this.prodIds.map(
              (k) => this.prodsById[k][0].name
            ); // first is the one with the highest version as well
            this.preselectProduct(filtered);
          });
        break;
    }
  }

  /* template */ importVersions() {
    if (!this.selectedVersions?.length) {
      this.areas.closePanel();
      return;
    }

    this.importing$.next(true);
    this.products
      .importProduct(this.selectedVersions, this.selectedRepo.name)
      .pipe(finalize(() => this.importing$.next(false)))
      .subscribe(() => this.areas.closePanel());
  }
}
