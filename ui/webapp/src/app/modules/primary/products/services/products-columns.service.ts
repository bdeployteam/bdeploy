import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { ProductDto } from 'src/app/models/gen.dtos';
import { ProductVersionDetailsCellComponent } from 'src/app/modules/panels/instances/components/product-version-details-cell/product-version-details-cell.component';

@Injectable({
  providedIn: 'root',
})
export class ProductsColumnsService {
  private readonly productNameColumn: BdDataColumn<ProductDto, string> = {
    id: 'name',
    name: 'Name',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.name,
    isId: true,
    sortCard: true,
  };

  public readonly productVersionColumn: BdDataColumn<ProductDto, string> = {
    id: 'version',
    name: 'Version',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => r.key.tag,
    isId: true,
    tooltip: () => null,
    component: ProductVersionDetailsCellComponent,
    sortCard: true,
  };

  private readonly productVendorColumn: BdDataColumn<ProductDto, string> = {
    id: 'vendor',
    name: 'Vendor',
    hint: BdDataColumnTypeHint.FOOTER,
    data: (r) => r.vendor,
  };

  private readonly productLogoCardColumn: BdDataColumn<ProductDto, string> = {
    id: 'logo',
    name: 'Logo',
    hint: BdDataColumnTypeHint.AVATAR,
    display: BdDataColumnDisplay.CARD,
    data: () => '/assets/no-image.svg',
  };

  public readonly defaultProductsColumns: BdDataColumn<ProductDto, unknown>[] = [
    this.productNameColumn,
    this.productVersionColumn,
    this.productVendorColumn,
    this.productLogoCardColumn,
  ];

  public readonly defaultReducedProductsColumns: BdDataColumn<ProductDto, unknown>[] = [
    this.productNameColumn,
    this.productVersionColumn,
  ];
}
