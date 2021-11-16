import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, combineLatest, Observable, Subscription } from 'rxjs';
import { debounceTime, finalize, map } from 'rxjs/operators';
import { ManifestKey, ObjectChangeType, ProductDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { ObjectChangesService } from 'src/app/modules/core/services/object-changes.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { RepositoriesService } from './repositories.service';

@Injectable({
  providedIn: 'root',
})
export class RepositoryService {
  private repository: string;
  private subscription: Subscription;

  private productsApiPath = (r) => `${this.cfg.config.api}/group/${r}/product`;
  private softwareRepositoryApiPath = (r) => `${this.cfg.config.api}/softwarerepository/${r}/content`;

  private update$ = new BehaviorSubject<any>(null);

  products$ = new BehaviorSubject<ProductDto[]>([]);
  productsLoading$ = new BehaviorSubject<boolean>(true);

  softwarePackages$ = new BehaviorSubject<ManifestKey[]>([]);
  softwarePackagesLoading$ = new BehaviorSubject<boolean>(true);

  // <any> is a ProductDto with:
  //  type: 'Product' / 'External Software'
  //  key:  key of product / manifest key of external package
  //  ...ProductDto properties / undefined
  data$: Observable<any> = combineLatest([
    this.products$.pipe(map((products) => products.map((product) => ({ type: 'Product', ...product })))),
    this.softwarePackages$.pipe(map((softwarePackages) => softwarePackages.map((manifestKey) => ({ type: 'External Software', key: manifestKey })))),
  ]).pipe(map(([products, softwarePackages]) => [...products, ...softwarePackages]));

  loading$: Observable<boolean> = combineLatest([this.productsLoading$, this.softwarePackagesLoading$]).pipe(map(([pl, el]) => pl || el));

  /** the current selection */
  current$ = new BehaviorSubject<any>(null);

  constructor(private cfg: ConfigService, private http: HttpClient, private changes: ObjectChangesService, repositories: RepositoriesService) {
    repositories.current$.subscribe((repository) => this.update$.next(repository?.name));
    this.update$.pipe(debounceTime(100)).subscribe((r) => this.reload(r));
  }

  public getUploadURL() {
    return `${this.softwareRepositoryApiPath(this.repository)}/upload-raw-content`;
  }

  public getImportURL() {
    return `${this.softwareRepositoryApiPath(this.repository)}/import-raw-content`;
  }

  private reload(repository: string) {
    if (!repository) {
      this.products$.next([]);
      this.softwarePackages$.next([]);
      this.updateChangeSubscription(null);
      return;
    }

    if (this.repository !== repository) {
      this.updateChangeSubscription(repository);
    }

    this.repository = repository;

    this.reloadProducts();
    this.reloadSoftwarePackages();
  }

  private reloadProducts() {
    this.productsLoading$.next(true);
    this.http
      .get<ProductDto[]>(`${this.productsApiPath(this.repository)}/list`)
      .pipe(
        finalize(() => this.productsLoading$.next(false)),
        measure('Products Load')
      )
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
        measure('External Software Packages Load')
      )
      .subscribe((result) => {
        this.softwarePackages$.next(result);
      });
  }

  private updateChangeSubscription(repository: string) {
    if (!!this.subscription) {
      this.subscription.unsubscribe();
    }

    if (!!repository) {
      this.subscription = this.changes.subscribe(ObjectChangeType.SOFTWARE_PACKAGE, { scope: [repository] }, (change) => {
        this.update$.next(this.repository);
      });

      this.subscription.add(
        this.changes.subscribe(ObjectChangeType.PRODUCT, { scope: [repository] }, (change) => {
          this.update$.next(this.repository);
        })
      );
    }
  }
}
