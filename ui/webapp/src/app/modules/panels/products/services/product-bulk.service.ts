import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, concat } from 'rxjs';
import { concatAll } from 'rxjs/operators';
import { ProductDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';

@Injectable({
  providedIn: 'root',
})
export class ProductBulkService {
  private cfg = inject(ConfigService);
  private http = inject(HttpClient);
  private groups = inject(GroupsService);
  private products = inject(ProductsService);
  private areas = inject(NavAreasService);

  public selection$ = new BehaviorSubject<ProductDto[]>([]);

  private apiPath = (group) => `${this.cfg.config.api}/group/${group}/product`;

  constructor() {
    // clear selection when the primary route changes
    this.areas.primaryRoute$.subscribe(() => this.selection$.next([]));

    // find matching selected instances if possible once instances change.
    this.products.products$.subscribe((prods) => {
      const newSelection: ProductDto[] = [];
      this.selection$.value.forEach((sel) => {
        const found = prods.find((p) => p.key.name === sel.key.name && p.key.tag === sel.key.tag);
        if (found) {
          newSelection.push(found);
        }
      });
      this.selection$.next(newSelection);
    });
  }

  public delete() {
    return concat(
      this.selection$.value.map((p) =>
        this.http.delete(`${this.apiPath(this.groups.current$.value.name)}/${p.key.name}/${p.key.tag}`)
      )
    ).pipe(concatAll());
  }
}
