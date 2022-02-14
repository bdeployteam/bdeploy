import { Injectable } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDataSizeCellComponent } from 'src/app/modules/core/components/bd-data-size-cell/bd-data-size-cell.component';

const COL_AVATAR: BdDataColumn<RemoteDirectoryEntry> = {
  id: 'avatar',
  name: '',
  data: () => 'subject',
  width: '40px',
  component: BdDataIconCellComponent,
};

const COL_PATH: BdDataColumn<RemoteDirectoryEntry> = {
  id: 'path',
  name: 'Path',
  data: (r) => r.path,
};

const COL_SIZE: BdDataColumn<RemoteDirectoryEntry> = {
  id: 'size',
  name: 'Size',
  data: (r) => r.size,
  width: '80px',
  component: BdDataSizeCellComponent,
};

const COL_MODIFIED: BdDataColumn<RemoteDirectoryEntry> = {
  id: 'modified',
  name: 'Last Modified',
  data: (r) => r.lastModified,
  width: '130px',
  component: BdDataDateCellComponent,
};

@Injectable({
  providedIn: 'root',
})
export class LogColumnsService {
  public defaultColumns: BdDataColumn<RemoteDirectoryEntry>[] = [
    COL_AVATAR,
    COL_PATH,
    COL_SIZE,
    COL_MODIFIED,
  ];
}
