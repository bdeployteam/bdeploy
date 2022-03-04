import { StepperSelectionEvent } from '@angular/cdk/stepper';
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatStepper } from '@angular/material/stepper';
import { groupBy } from 'lodash-es';
import { BehaviorSubject, combineLatest, finalize, forkJoin, map } from 'rxjs';
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

  @ViewChild(MatStepper) stepper: MatStepper;

  constructor(
    private repositories: RepositoriesService,
    private repository: RepositoryService,
    private products: ProductsService,
    private prodCols: ProductsColumnsService,
    private areas: NavAreasService
  ) {}

  ngOnInit(): void {
    this.repositories.repositories$.subscribe((repos) => {
      this.repos = repos;
      this.repoLabels = repos.map((r) => r.name);
    });
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
    forkJoin(
      this.selectedVersions.map((p) =>
        this.products.importProduct(p, this.selectedRepo.name)
      )
    )
      .pipe(finalize(() => this.importing$.next(false)))
      .subscribe(() => this.areas.closePanel());
  }
}
