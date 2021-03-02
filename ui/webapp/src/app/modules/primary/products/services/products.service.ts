import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ObjectChangeType, ProductDto } from 'src/app/models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';
import { NavAreasService } from '../../../core/services/nav-areas.service';
import { ObjectChangesService } from '../../../core/services/object-changes.service';

@Injectable({
  providedIn: 'root',
})
export class ProductsService {
  loading$ = new BehaviorSubject<boolean>(true);
  products$ = new BehaviorSubject<ProductDto[]>([]);

  private group: string;
  private subscription: Subscription;

  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/product`;

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private changes: ObjectChangesService,
    areas: NavAreasService
  ) {
    areas.groupContext$.subscribe((group) => this.reload(group));
  }

  public getUploadURL() {
    return `${this.apiPath(this.group)}/upload`;
  }

  private reload(group: string) {
    if (!group) {
      return;
    }

    if (this.group !== group) {
      this.updateChangeSubscription(group);
    }

    this.group = group;
    this.loading$.next(true);
    this.http
      .get<ProductDto[]>(`${this.apiPath(group)}/list`)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((prods) => {
        this.products$.next(prods);
      });
  }

  private updateChangeSubscription(group: string) {
    if (!!this.subscription) {
      this.subscription.unsubscribe();
    }

    this.subscription = this.changes.subscribe(ObjectChangeType.PRODUCT, { scope: [group] }, () => {
      this.reload(group);
    });
  }
}
