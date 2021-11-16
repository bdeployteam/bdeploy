import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ApplicationDto, ObjectChangeType, ProductDto } from 'src/app/models/gen.dtos';
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

  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/product`;

  constructor(private cfg: ConfigService, private http: HttpClient, private changes: ObjectChangesService, groups: GroupsService) {
    groups.current$.subscribe((group) => this.reload(group?.name));
  }

  public getUploadURL() {
    return `${this.apiPath(this.group)}/upload`;
  }

  public loadApplications(prod: ProductDto): Observable<ApplicationDto[]> {
    return this.http
      .get<ApplicationDto[]>(`${this.apiPath(this.group)}/${prod.key.name}/${prod.key.tag}/application`)
      .pipe(measure('Load Applications of Product'));
  }

  private reload(group: string) {
    if (!group) {
      this.products$.next([]);
      this.updateChangeSubscription(null);
      return;
    }

    if (this.group !== group) {
      this.updateChangeSubscription(group);
    }

    this.group = group;
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
    if (!!this.subscription) {
      this.subscription.unsubscribe();
    }

    if (!!group) {
      this.subscription = this.changes.subscribe(ObjectChangeType.PRODUCT, { scope: [group] }, () => {
        this.reload(group);
      });
    }
  }
}
