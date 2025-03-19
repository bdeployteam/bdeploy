import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { ProductDto } from 'src/app/models/gen.dtos';
import { MatCard } from '@angular/material/card';
import { MatChipListbox, MatChipOption } from '@angular/material/chips';
import { BdPopupDirective } from '../../../../core/components/bd-popup/bd-popup.directive';
import { CellComponent } from '../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-product-version-details-cell',
    templateUrl: './product-version-details-cell.component.html',
    imports: [MatCard, MatChipListbox, MatChipOption, BdPopupDirective]
})
export class ProductVersionDetailsCellComponent implements OnInit, CellComponent<ProductDto, string> {

  @Input() record: ProductDto;
  @Input() column: BdDataColumn<ProductDto, string>;

  protected labels: { a: string; b: string }[] = [];

  ngOnInit(): void {
    for (const key of Object.keys(this.record.labels)) {
      this.labels.push({ a: key, b: this.record.labels[key] });
    }
  }

}
