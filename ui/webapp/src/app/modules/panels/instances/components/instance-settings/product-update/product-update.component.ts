import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subscription, combineLatest, map } from 'rxjs';
import { BdDataColumn, BdDataColumnTypeHint } from 'src/app/models/data';
import { ProductDto } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { ProductVersionDetailsCellComponent } from '../../product-version-details-cell/product-version-details-cell.component';
import { UpdateActionComponent } from './update-action/update-action.component';

@Component({
  selector: 'app-product-update',
  templateUrl: './product-update.component.html',
})
export class ProductUpdateComponent implements OnInit, OnDestroy {
  public products = inject(ProductsService);
  public edit = inject(InstanceEditService);
  public areas = inject(NavAreasService);
  private readonly groups = inject(GroupsService);

  private readonly productVersionColumn: BdDataColumn<ProductDto> = {
    id: 'version',
    name: 'Version',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => `${r.key.tag}${this.isCurrent(r) ? ' - current' : ''}`,
    isId: true,
    tooltip: () => null,
    component: ProductVersionDetailsCellComponent,
  };

  private readonly productUpdateAction: BdDataColumn<ProductDto> = {
    id: 'update',
    name: 'Upd.',
    data: (r) => r.key.tag,
    component: UpdateActionComponent,
    width: '40px',
  };

  protected records$ = new BehaviorSubject<ProductDto[]>(null);
  protected readonly columns: BdDataColumn<ProductDto>[] = [this.productVersionColumn, this.productUpdateAction];

  private subscription: Subscription;

  protected newerVersionAvailableOnManaged$: Observable<boolean>;

  ngOnInit() {
    this.subscription = combineLatest([this.edit.state$, this.products.products$]).subscribe(([state, prods]) => {
      if (!state || !prods?.length) {
        this.records$.next(null);
        return;
      }

      this.records$.next(
        prods
          .filter((p) => p.key.name === state.config.config.product.name)
          .filter(
            (p) =>
              this.isCurrent(p) || this.matchesProductFilterRegex(p.key.tag, state.config.config.productFilterRegex),
          ),
      );
    });

    this.newerVersionAvailableOnManaged$ = combineLatest([this.edit.current$, this.edit.state$]).pipe(
      map(([current, state]) => {
        if (!current || !state) {
          return false;
        }
        const productName = state.config.config.product.name;
        return current?.managedServer?.productUpdates?.newerVersionAvailable[productName];
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private isCurrent(product: ProductDto): boolean {
    return this.edit.state$.value.config.config.product.tag === product.key.tag;
  }

  private matchesProductFilterRegex(productTag: string, productFilterRegex: string): boolean {
    // if there is no filter, all products are eligible
    if (!productFilterRegex) return true;
    return new RegExp(productFilterRegex).test(productTag);
  }

  protected goToProductSync() {
    const group = this.groups.current$.value.name;
    const managed = this.edit.current$.value.managedServer.hostName;
    this.areas.navigateBoth(['products', 'browser', group], ['panels', 'products', 'sync', 'CENTRAL', managed]);
  }
}
