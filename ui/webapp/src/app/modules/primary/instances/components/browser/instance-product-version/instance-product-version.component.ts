import { Component, Input, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';

@Component({
  selector: 'app-instance-product-version',
  templateUrl: './instance-product-version.component.html',
  styleUrls: ['./instance-product-version.component.css'],
})
export class InstanceProductVersionComponent {
  private readonly products = inject(ProductsService);

  @Input() record: InstanceDto;

  get hasProduct$(): Observable<boolean> {
    const product = this.record?.instanceConfiguration?.product;
    return this.products.products$.pipe(
      map((products) => !!products?.find((p) => p.key.name === product?.name && p.key.tag === product?.tag)),
    );
  }
}
