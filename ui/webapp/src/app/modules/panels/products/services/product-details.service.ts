import { HttpClient } from '@angular/common/http';
import { Injectable, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import {
  InstanceUsageDto,
  ManifestKey,
  PluginInfoDto,
  ProductDto,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';

export interface LabelRecord {
  key: string;
  value: string;
}

/**
 * A service which extracts a single product denoted by route parameters. This requires the active route to have
 * a 'key' and a 'tag' route parameter. The service will listen to product data from the products service.
 * <p>
 * This service is *not* provided in root. Any component providing this service *must* have the 'key' and 'tag'
 * route parameters.
 */
@Injectable()
export class ProductDetailsService implements OnDestroy {
  public productKey$ = new BehaviorSubject<string>(null);
  public productTag$ = new BehaviorSubject<string>(null);
  public product$ = new BehaviorSubject<ProductDto>(null);
  public labels$ = new BehaviorSubject<LabelRecord[]>(null);

  private usedIn$ = new BehaviorSubject<InstanceUsageDto[]>(null);
  public usedInLoading$ = new BehaviorSubject<boolean>(false);

  private plugins$ = new BehaviorSubject<PluginInfoDto[]>(null);
  public pluginsLoading$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;
  private prodSubscription: Subscription;

  private apiPath = () =>
    `${this.cfg.config.api}/group/${this.areas.groupContext$.value}/product/${this.productKey$.value}/${this.productTag$.value}`;
  private pluginApiPath = () =>
    `${this.cfg.config.api}/plugin-admin/list-product-plugins/${this.areas.groupContext$.value}`;

  constructor(
    private products: ProductsService,
    private route: ActivatedRoute,
    private areas: NavAreasService,
    private cfg: ConfigService,
    private http: HttpClient,
    private downloads: DownloadService
  ) {
    this.subscription = this.route.paramMap.subscribe((p) => {
      this.productKey$.next(p.get('key'));
      this.productTag$.next(p.get('tag'));

      if (this.prodSubscription) {
        this.prodSubscription.unsubscribe();
      }
      this.prodSubscription = this.products.products$
        .pipe(
          map((prods) =>
            prods.find(
              (e) =>
                e.key.name === this.productKey$.value &&
                e.key.tag === this.productTag$.value
            )
          )
        )
        .subscribe((prod) => {
          this.usedIn$.next(null);
          this.product$.next(prod);
          this.labels$.next(prod ? this.mapLabels(prod) : null);
        });
    });
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
    this.prodSubscription.unsubscribe();
  }

  public getUsedIn(): Observable<InstanceUsageDto[]> {
    if (!this.usedIn$.value && !this.usedInLoading$.value) {
      this.usedInLoading$.next(true);
      this.http
        .get<InstanceUsageDto[]>(`${this.apiPath()}/usedIn`)
        .pipe(finalize(() => this.usedInLoading$.next(false)))
        .subscribe((u) => this.usedIn$.next(u));
    }
    return this.usedIn$.asObservable();
  }

  public getPlugins(): Observable<PluginInfoDto[]> {
    if (!this.plugins$.value && !this.pluginsLoading$.value) {
      this.pluginsLoading$.next(true);
      const key: ManifestKey = {
        name: this.productKey$.value,
        tag: this.productTag$.value,
      };
      this.http
        .post<PluginInfoDto[]>(this.pluginApiPath(), key)
        .pipe(finalize(() => this.pluginsLoading$.next(false)))
        .subscribe((p) => this.plugins$.next(p));
    }
    return this.plugins$;
  }

  public delete(): Observable<any> {
    return this.http.delete(this.apiPath());
  }

  public download(): Observable<any> {
    return new Observable<any>((s) => {
      this.http
        .get(`${this.apiPath()}/zip`, { responseType: 'text' })
        .subscribe((token) => {
          this.downloads.download(this.downloads.createDownloadUrl(token));
          s.next(token);
          s.complete();
        });
    });
  }

  private mapLabels(prod: ProductDto) {
    const labels: LabelRecord[] = [];
    for (const k of Object.keys(prod.labels)) {
      labels.push({ key: k, value: prod.labels[k] });
    }
    return labels;
  }
}
