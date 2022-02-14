import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, Subject, Subscription } from 'rxjs';
import { debounceTime, finalize } from 'rxjs/operators';
import {
  ApplicationDto,
  ObjectChangeType,
  ProductDto,
} from 'src/app/models/gen.dtos';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { ConfigService } from '../../../core/services/config.service';
import { ObjectChangesService } from '../../../core/services/object-changes.service';
import { GroupsService } from '../../groups/services/groups.service';

@Injectable({
  providedIn: 'root',
})
export class ProductsService {
  loading$ = new BehaviorSubject<boolean>(true);
  products$ = new BehaviorSubject<ProductDto[]>([]);

  private group: string;
  private subscription: Subscription;
  private delayLoad$ = new Subject<string>();

  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/product`;
  public uploadUrl$ = new BehaviorSubject<string>(null);

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private changes: ObjectChangesService,
    groups: GroupsService
  ) {
    groups.current$.subscribe((group) => this.load(group?.name));
    this.delayLoad$
      .pipe(debounceTime(100))
      .subscribe((group) => this.load(group));
  }

  public loadApplications(prod: ProductDto): Observable<ApplicationDto[]> {
    return this.http
      .get<ApplicationDto[]>(
        `${this.apiPath(this.group)}/${prod.key.name}/${
          prod.key.tag
        }/application`
      )
      .pipe(measure('Load Applications of Product'));
  }

  public reload() {
    this.load(this.group);
  }

  public importProduct(prod: ProductDto, repo: string) {
    const params = {
      repo: repo,
      name: prod.key.name,
      tag: prod.key.tag,
    };
    return this.http
      .post(`${this.apiPath(this.group)}/copy`, null, { params })
      .pipe(measure('Import Product'));
  }

  private load(group: string) {
    if (!group) {
      this.products$.next([]);
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
        measure('Product Load')
      )
      .subscribe((prods) => {
        this.products$.next(prods);
      });
  }

  private updateChangeSubscription(group: string) {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }

    if (group) {
      this.subscription = this.changes.subscribe(
        ObjectChangeType.PRODUCT,
        { scope: [group] },
        () => {
          this.delayLoad$.next(group);
        }
      );
    }
  }
}
