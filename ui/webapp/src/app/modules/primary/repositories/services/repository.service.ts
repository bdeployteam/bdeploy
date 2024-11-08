import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subscription, combineLatest } from 'rxjs';
import { debounceTime, finalize, map } from 'rxjs/operators';
import { ManifestKey, ObjectChangeType, ProductDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { ObjectChangesService } from 'src/app/modules/core/services/object-changes.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { RepositoriesService } from './repositories.service';

export interface ProdDtoWithType extends ProductDto {
  type: SwPkgType;
}

export interface SwDtoWithType {
  type: SwPkgType;
  key: ManifestKey;
  requiredByProduct: boolean;
}

export type SwPkgCompound = ProdDtoWithType | SwDtoWithType;

export enum SwPkgType {
  PRODUCT = 'Product',
  EXTERNAL_SOFTWARE = 'External Software',
}

@Injectable({
  providedIn: 'root',
})
export class RepositoryService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly changes = inject(ObjectChangesService);
  private readonly repositories = inject(RepositoriesService);

  private repository: string;
  private subscription: Subscription;

  private readonly productsApiPath = (r) => `${this.cfg.config.api}/softwarerepository/${r}/product`;
  private readonly softwareRepositoryApiPath = (r) => `${this.cfg.config.api}/softwarerepository/${r}/content`;
  public uploadUrl$ = new BehaviorSubject<string>(null);
  public importUrl$ = new BehaviorSubject<string>(null);

  private readonly update$ = new BehaviorSubject<string>(null);

  public products$ = new BehaviorSubject<ProductDto[]>([]);
  public productsLoading$ = new BehaviorSubject<boolean>(true);

  public softwarePackages$ = new BehaviorSubject<ManifestKey[]>([]);
  public softwarePackagesLoading$ = new BehaviorSubject<boolean>(true);

  public data$: Observable<SwPkgCompound[]> = combineLatest([this.products$, this.softwarePackages$]).pipe(
    map(([products, softwarePackages]) => [
      ...products.map((product) => ({ type: SwPkgType.PRODUCT, ...product })),
      ...softwarePackages.map((manifestKey) => ({
        type: SwPkgType.EXTERNAL_SOFTWARE,
        key: manifestKey,
        requiredByProduct: this.isRequiredByProduct(manifestKey, products),
      })),
    ]),
  );

  public loading$: Observable<boolean> = combineLatest([this.productsLoading$, this.softwarePackagesLoading$]).pipe(
    map(([pl, el]) => pl || el),
  );

  constructor() {
    this.repositories.current$.subscribe((repository) => {
      // whenever the current repo changes, we trigger a delayed reload.
      // we *anyhow* want to remove the outdated data before doing this.
      // otherwise the user would briefly see the old data before loading begins.
      this.productsLoading$.next(true);
      this.softwarePackagesLoading$.next(true);
      this.products$.next([]);
      this.softwarePackages$.next([]);

      // trigger delayed update.
      this.update$.next(repository?.name);
    });
    this.update$.pipe(debounceTime(100)).subscribe((r) => this.reload(r));
  }

  public loadProductsOf(repository: string): Observable<ProductDto[]> {
    return this.http.get<ProductDto[]>(`${this.productsApiPath(repository)}/list`).pipe(measure('Products Load'));
  }

  private reload(repository: string) {
    if (!repository) {
      this.repository = null;
      this.products$.next([]);
      this.softwarePackages$.next([]);
      this.updateChangeSubscription(null);
      return;
    }

    if (this.repository !== repository) {
      this.updateChangeSubscription(repository);
    }

    this.repository = repository;
    this.uploadUrl$.next(`${this.softwareRepositoryApiPath(this.repository)}/upload-raw-content`);
    this.importUrl$.next(`${this.softwareRepositoryApiPath(this.repository)}/import-raw-content`);

    this.reloadProducts();
    this.reloadSoftwarePackages();
  }

  private reloadProducts() {
    this.productsLoading$.next(true);
    this.loadProductsOf(this.repository)
      .pipe(finalize(() => this.productsLoading$.next(false)))
      .subscribe((result) => {
        this.products$.next(result);
      });
  }

  private reloadSoftwarePackages() {
    let params = new HttpParams();
    params = params.set('generic', 'true');
    this.softwarePackagesLoading$.next(true);
    this.http
      .get<ManifestKey[]>(`${this.softwareRepositoryApiPath(this.repository)}`, { params: params })
      .pipe(
        finalize(() => this.softwarePackagesLoading$.next(false)),
        measure('External Software Packages Load'),
      )
      .subscribe((result) => {
        this.softwarePackages$.next(result);
      });
  }

  private updateChangeSubscription(repository: string) {
    this.subscription?.unsubscribe();
    this.subscription = null;

    if (repository) {
      this.subscription = this.changes.subscribe(ObjectChangeType.SOFTWARE_PACKAGE, { scope: [repository] }, () => {
        this.update$.next(this.repository);
      });

      this.subscription.add(
        this.changes.subscribe(ObjectChangeType.PRODUCT, { scope: [repository] }, () => {
          this.update$.next(this.repository);
        }),
      );
    }
  }

  private isRequiredByProduct(key: ManifestKey, products: ProductDto[]): boolean {
    const references = products.reduce((acc, product) => acc.concat(product.references), []);
    return references.some((reference) => key.name === reference.name && key.tag === reference.tag);
  }
}
