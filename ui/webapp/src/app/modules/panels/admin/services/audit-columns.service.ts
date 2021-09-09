import { Injectable } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { AuditLogDto } from 'src/app/models/gen.dtos';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';

const COL_TIME: BdDataColumn<AuditLogDto> = {
  id: 'time',
  name: 'Time',
  data: (r) => r.instant,
  width: '100px',
  component: BdDataDateCellComponent,
};

const COL_THREAD: BdDataColumn<AuditLogDto> = {
  id: 'thread',
  name: 'Thread',
  data: (r) => `${r.threadId} - ${r.thread}`,
};

const COL_LEVEL: BdDataColumn<AuditLogDto> = {
  id: 'level',
  name: 'Level',
  data: (r) => r.level,
  width: '80px',
};

const COL_MSG: BdDataColumn<AuditLogDto> = {
  id: 'msg',
  name: 'Message',
  data: (r) => r.message,
};

const COL_WHO: BdDataColumn<AuditLogDto> = {
  id: 'who',
  name: 'Who',
  data: (r) => r.who,
  width: '100px',
};

const COL_WHAT: BdDataColumn<AuditLogDto> = {
  id: 'what',
  name: 'What',
  data: (r) => r.what,
};

const COL_METHOD: BdDataColumn<AuditLogDto> = {
  id: 'method',
  name: 'Method',
  data: (r) => r.method,
  width: '80px',
};

const COL_PARAMS: BdDataColumn<AuditLogDto> = {
  id: 'params',
  name: 'Parameters',
  data: (r) => r.parameters,
};

@Injectable({
  providedIn: 'root',
})
export class AuditColumnsService {
  public defaultColumns: BdDataColumn<AuditLogDto>[] = [COL_TIME, COL_THREAD, COL_LEVEL, COL_MSG, COL_WHO, COL_WHAT, COL_METHOD, COL_PARAMS];

  constructor() {}
}
