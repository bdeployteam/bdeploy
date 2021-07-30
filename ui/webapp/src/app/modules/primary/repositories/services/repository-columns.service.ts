import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { RepositoryService } from './repository.service';

@Injectable({
  providedIn: 'root',
})
export class RepositoryColumnsService {
  nameColumn: BdDataColumn<any> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.key.name,
  };

  productNameColumn: BdDataColumn<any> = {
    id: 'productName',
    name: 'Product Name',
    data: (r) => r.name,
  };

  versionColumn: BdDataColumn<any> = {
    id: 'version',
    name: 'Version',
    data: (r) => r.key.tag,
  };

  productVendorColumn: BdDataColumn<any> = {
    id: 'vendor',
    name: 'Vendor',
    data: (r) => r.vendor,
  };

  cardTitle: BdDataColumn<any> = {
    id: 'cardTitle',
    name: 'Name',
    hint: BdDataColumnTypeHint.TITLE,
    display: BdDataColumnDisplay.CARD,
    data: (r) => (r.type === 'Product' ? r.name : r.key.name),
  };

  cardDescription: BdDataColumn<any> = {
    id: 'cardDescription',
    name: 'Description',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    display: BdDataColumnDisplay.CARD,
    data: (r) => (r.type === 'Product' ? r.key.name + ' ' : '') + r.key.tag,
  };

  cardLogo: BdDataColumn<any> = {
    id: 'cardLogo',
    name: 'Logo',
    hint: BdDataColumnTypeHint.AVATAR,
    display: BdDataColumnDisplay.CARD,
    data: (r) => '/assets/no-image.svg',
  };

  cardVendor: BdDataColumn<any> = {
    id: 'cardvendor',
    name: 'Vendor',
    hint: BdDataColumnTypeHint.FOOTER,
    display: BdDataColumnDisplay.CARD,
    data: (r) => '-',
  };

  defaultRepositoryColumns: BdDataColumn<any>[] = [
    this.nameColumn,
    this.productNameColumn,
    this.versionColumn,
    this.productVendorColumn,
    this.cardTitle,
    this.cardDescription,
    this.cardLogo,
    this.cardVendor,
  ];

  constructor(private repository: RepositoryService) {}
}
