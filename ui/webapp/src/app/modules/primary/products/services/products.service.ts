import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, Observable, Subject, Subscription } from 'rxjs';
import { debounceTime, finalize } from 'rxjs/operators';
import { ApplicationDto, ObjectChangeType, ProductDto } from 'src/app/models/gen.dtos';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { InstTemplateData } from 'src/app/modules/core/services/product-actions-columns';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { ConfigService } from '../../../core/services/config.service';
import { ObjectChangesService } from '../../../core/services/object-changes.service';
import { GroupsService } from '../../groups/services/groups.service';

@Injectable({
  providedIn: 'root',
})
export class ProductsService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly changes = inject(ObjectChangesService);
  private readonly groups = inject(GroupsService);
  private readonly downloads = inject(DownloadService);

  public loading$ = new BehaviorSubject<boolean>(true);
  public products$ = new BehaviorSubject<ProductDto[]>(null);

  private group: string;
  private subscription: Subscription;
  private readonly delayLoad$ = new Subject<string>();

  private readonly apiPath = (g) => `${this.cfg.config.api}/group/${g}/product`;
  public uploadUrl$ = new BehaviorSubject<string>(null);

  constructor() {
    this.groups.current$.subscribe((group) => this.load(group?.name));
    this.delayLoad$.pipe(debounceTime(100)).subscribe((group) => this.load(group));
  }

  public loadApplications(prod: ProductDto): Observable<ApplicationDto[]> {
    return this.http
      .get<ApplicationDto[]>(`${this.apiPath(this.group)}/${prod.key.name}/${prod.key.tag}/application`)
      .pipe(measure('Load Applications of Product'));
  }

  public reload() {
    this.load(this.group);
  }

  public importProduct(products: ProductDto[], repo: string) {
    const params = {
      repo: repo,
      name: products[0].key.name, // products array contains different versions of the same product, so its fine
      tags: products.map((prod) => prod.key.tag),
    };
    return this.http.post(`${this.apiPath(this.group)}/copy`, null, { params }).pipe(measure('Import Product'));
  }

  public loadProductsOf(group: string): Observable<ProductDto[]> {
    return this.http.get<ProductDto[]>(`${this.apiPath(group)}/list`);
  }

  public downloadResponseFile(data: InstTemplateData, version: string) {
    const instanceTemplate = data.config.name;
    this.http
      .get(`${this.apiPath(this.group)}/get-response-file`, {
        params: { productId: data.productId, version, instanceTemplate },
        responseType: 'text',
      })
      .subscribe((yaml) => {
        this.downloads.downloadYaml(this.downloads.buildResponseFileName(data.productName, instanceTemplate), yaml);
      });
  }

  private load(group: string) {
    if (!group) {
      this.group = null;
      this.products$.next(null);
      this.updateChangeSubscription(null);
      return;
    }

    if (this.group !== group) {
      this.updateChangeSubscription(group);
    }

    this.group = group;
    this.uploadUrl$.next(`${this.apiPath(this.group)}/upload`);
    this.loading$.next(true);
    this.http
      .get<ProductDto[]>(`${this.apiPath(group)}/list`)
      .pipe(
        finalize(() => this.loading$.next(false)),
        measure('Product Load'),
      )
      .subscribe((prods) => {
        this.products$.next(prods);
      });
  }

  private updateChangeSubscription(group: string) {
    this.subscription?.unsubscribe();
    this.subscription = null;

    if (group) {
      this.subscription = this.changes.subscribe(ObjectChangeType.PRODUCT, { scope: [group] }, () => {
        this.delayLoad$.next(group);
      });
    }
  }
}
