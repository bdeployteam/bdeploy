import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { ProductDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-product-version-details-cell',
  templateUrl: './product-version-details-cell.component.html',
})
export class ProductVersionDetailsCellComponent implements OnInit {
  @Input() record: ProductDto;
  @Input() column: BdDataColumn<ProductDto>;

  protected labels: { a: string; b: string }[] = [];

  ngOnInit(): void {
    for (const key of Object.keys(this.record.labels)) {
      this.labels.push({ a: key, b: this.record.labels[key] });
    }
  }
}
