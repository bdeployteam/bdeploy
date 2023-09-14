import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { ProdDtoWithType, SwPkgCompound } from './repository.service';

@Injectable({
  providedIn: 'root',
})
export class RepositoryColumnsService {
  private nameColumn: BdDataColumn<SwPkgCompound> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.key.name,
    isId: true,
  };

  private productNameColumn: BdDataColumn<ProdDtoWithType> = {
    id: 'productName',
    name: 'Product Name',
    data: (r) => r.name,
  };

  private versionColumn: BdDataColumn<SwPkgCompound> = {
    id: 'version',
    name: 'Version',
    data: (r) => r.key.tag,
    isId: true,
  };

  private productVendorColumn: BdDataColumn<ProdDtoWithType> = {
    id: 'vendor',
    name: 'Vendor',
    data: (r) => r.vendor,
  };

  private cardTitle: BdDataColumn<ProdDtoWithType> = {
    id: 'cardTitle',
    name: 'Name',
    hint: BdDataColumnTypeHint.TITLE,
    display: BdDataColumnDisplay.CARD,
    data: (r) => (r.type === 'Product' ? r.name : r.key.name),
  };

  private cardDescription: BdDataColumn<ProdDtoWithType> = {
    id: 'cardDescription',
    name: 'Description',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    display: BdDataColumnDisplay.CARD,
    data: (r) => (r.type === 'Product' ? r.key.name + ' ' : '') + r.key.tag,
  };

  private cardLogo: BdDataColumn<ProdDtoWithType> = {
    id: 'cardLogo',
    name: 'Logo',
    hint: BdDataColumnTypeHint.AVATAR,
    display: BdDataColumnDisplay.CARD,
    data: () => '/assets/no-image.svg',
  };

  private cardVendor: BdDataColumn<ProdDtoWithType> = {
    id: 'cardvendor',
    name: 'Vendor',
    hint: BdDataColumnTypeHint.FOOTER,
    display: BdDataColumnDisplay.CARD,
    data: () => '-',
  };

  public defaultRepositoryColumns: BdDataColumn<SwPkgCompound>[] = [
    this.nameColumn,
    this.productNameColumn,
    this.versionColumn,
    this.productVendorColumn,
    this.cardTitle,
    this.cardDescription,
    this.cardLogo,
    this.cardVendor,
  ];
}
