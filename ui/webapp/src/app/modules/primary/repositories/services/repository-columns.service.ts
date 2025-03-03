import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { SwRepositoryEntry, SwPkgType } from './repository.service';

@Injectable({
  providedIn: 'root',
})
export class RepositoryColumnsService {
  private readonly nameColumn: BdDataColumn<SwRepositoryEntry> = {
    id: 'key',
    name: 'Key',
    data: (r) => r.key.name,
    isId: true,
  };

  private readonly productNameColumn: BdDataColumn<SwRepositoryEntry> = {
    id: 'productName',
    name: 'Product Name',
    data: (r) => r.name,
  };

  private readonly versionColumn: BdDataColumn<SwRepositoryEntry> = {
    id: 'version',
    name: 'Version',
    data: (r) => r.key.tag,
    isId: true,
  };

  private readonly productVendorColumn: BdDataColumn<SwRepositoryEntry> = {
    id: 'vendor',
    name: 'Vendor',
    data: (r) => r.vendor,
  };

  private readonly cardTitle: BdDataColumn<SwRepositoryEntry> = {
    id: 'cardTitle',
    name: 'Name',
    hint: BdDataColumnTypeHint.TITLE,
    display: BdDataColumnDisplay.CARD,
    data: (r) => (r.type === SwPkgType.PRODUCT ? r.name : r.key.name),
  };

  private readonly cardDescription: BdDataColumn<SwRepositoryEntry> = {
    id: 'cardDescription',
    name: 'Description',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    display: BdDataColumnDisplay.CARD,
    data: (r) => (r.type === SwPkgType.PRODUCT ? r.key.name + ' ' : '') + r.key.tag,
  };

  private readonly cardLogo: BdDataColumn<SwRepositoryEntry> = {
    id: 'cardLogo',
    name: 'Logo',
    hint: BdDataColumnTypeHint.AVATAR,
    display: BdDataColumnDisplay.CARD,
    data: () => '/assets/no-image.svg',
  };

  private readonly cardVendor: BdDataColumn<SwRepositoryEntry> = {
    id: 'cardvendor',
    name: 'Vendor',
    hint: BdDataColumnTypeHint.FOOTER,
    display: BdDataColumnDisplay.CARD,
    data: () => '-',
  };

  public readonly defaultRepositoryColumns: BdDataColumn<SwRepositoryEntry>[] = [
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
