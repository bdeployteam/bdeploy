import { Component, ViewEncapsulation, inject } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { CleanupAction } from 'src/app/models/gen.dtos';
import { CleanupService } from '../../services/cleanup.service';

const colType: BdDataColumn<CleanupAction> = {
  id: 'type',
  name: 'Action',
  data: (r) => r.type,
  width: '150px',
};

const colWhat: BdDataColumn<CleanupAction> = {
  id: 'what',
  name: 'Target',
  data: (r) => r.what,
};

const colDesc: BdDataColumn<CleanupAction> = {
  id: 'description',
  name: 'Description',
  data: (r) => r.description,
};

@Component({
  selector: 'app-master-cleanup',
  templateUrl: './master-cleanup.component.html',
  encapsulation: ViewEncapsulation.None,
})
export class MasterCleanupComponent {
  protected cleanup = inject(CleanupService);

  protected readonly columns: BdDataColumn<CleanupAction>[] = [colType, colWhat, colDesc];
}
