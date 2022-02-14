import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { ProductDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-product-version-details',
  templateUrl: './product-version-details.component.html',
  styleUrls: ['./product-version-details.component.css'],
})
export class ProductVersionDetailsComponent implements OnInit {
  @Input() record: ProductDto;
  @Input() column: BdDataColumn<ProductDto>;

  /* template */ labels: { a: string; b: string }[] = [];

  ngOnInit(): void {
    for (const key of Object.keys(this.record.labels)) {
      this.labels.push({ a: key, b: this.record.labels[key] });
    }
  }
}
