import { Component, Input, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { MatTooltip } from '@angular/material/tooltip';
import { AsyncPipe } from '@angular/common';
import {
  TableCellDisplay
} from '../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';
import { BdDataColumn } from '../../../../../../models/data';

@Component({
    selector: 'app-instance-product-version',
    templateUrl: './instance-product-version.component.html',
    styleUrls: ['./instance-product-version.component.css'],
    imports: [MatTooltip, AsyncPipe]
})
export class InstanceProductVersionComponent implements TableCellDisplay<InstanceDto>{
  private readonly products = inject(ProductsService);

  @Input() record: InstanceDto;
  @Input() column: BdDataColumn<InstanceDto>;

  get hasProduct$(): Observable<boolean> {
    const product = this.record?.instanceConfiguration?.product;
    return this.products.products$.pipe(
      map((products) => !!products?.find((p) => p.key.name === product?.name && p.key.tag === product?.tag)),
    );
  }
}
