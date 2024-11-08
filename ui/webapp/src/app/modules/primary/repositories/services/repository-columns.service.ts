import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { ProdDtoWithType, SwPkgCompound, SwPkgType } from './repository.service';

@Injectable({
  providedIn: 'root',
})
export class RepositoryColumnsService {
  private readonly nameColumn: BdDataColumn<SwPkgCompound> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.key.name,
    isId: true,
  };

  private readonly productNameColumn: BdDataColumn<ProdDtoWithType> = {
    id: 'productName',
    name: 'Product Name',
    data: (r) => r.name,
  };

  private readonly versionColumn: BdDataColumn<SwPkgCompound> = {
    id: 'version',
    name: 'Version',
    data: (r) => r.key.tag,
    isId: true,
  };

  private readonly productVendorColumn: BdDataColumn<ProdDtoWithType> = {
    id: 'vendor',
    name: 'Vendor',
    data: (r) => r.vendor,
  };

  private readonly cardTitle: BdDataColumn<ProdDtoWithType> = {
    id: 'cardTitle',
    name: 'Name',
    hint: BdDataColumnTypeHint.TITLE,
    display: BdDataColumnDisplay.CARD,
    data: (r) => (r.type === SwPkgType.PRODUCT ? r.name : r.key.name),
  };

  private readonly cardDescription: BdDataColumn<ProdDtoWithType> = {
    id: 'cardDescription',
    name: 'Description',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    display: BdDataColumnDisplay.CARD,
    data: (r) => (r.type === SwPkgType.PRODUCT ? r.key.name + ' ' : '') + r.key.tag,
  };

  private readonly cardLogo: BdDataColumn<ProdDtoWithType> = {
    id: 'cardLogo',
    name: 'Logo',
    hint: BdDataColumnTypeHint.AVATAR,
    display: BdDataColumnDisplay.CARD,
    data: () => '/assets/no-image.svg',
  };

  private readonly cardVendor: BdDataColumn<ProdDtoWithType> = {
    id: 'cardvendor',
    name: 'Vendor',
    hint: BdDataColumnTypeHint.FOOTER,
    display: BdDataColumnDisplay.CARD,
    data: () => '-',
  };

  public readonly defaultRepositoryColumns: BdDataColumn<SwPkgCompound>[] = [
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
