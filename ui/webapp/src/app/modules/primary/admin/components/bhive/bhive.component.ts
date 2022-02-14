import { Component } from '@angular/core';
import { Sort } from '@angular/material/sort';
import { BehaviorSubject } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { HiveService } from '../../services/hive.service';

const COL_AVATAR: BdDataColumn<string> = {
  id: 'avatar',
  name: '',
  data: () => 'sd_storage',
  component: BdDataIconCellComponent,
  width: '30px',
};

const COL_ID: BdDataColumn<string> = {
  id: 'id',
  name: 'BHive',
  data: (r) => r,
};

@Component({
  selector: 'app-bhive',
  templateUrl: './bhive.component.html',
})
export class BHiveComponent {
  /* template */ records$ = new BehaviorSubject<string[]>(null);
  /* template */ columns: BdDataColumn<string>[] = [COL_AVATAR, COL_ID];
  /* template */ sort: Sort = { active: 'id', direction: 'asc' };

  /* template */ getRecordRoute = (row: string) => {
    return ['', { outlets: { panel: ['panels', 'admin', 'bhive', row] } }];
  };

  constructor(public hives: HiveService) {
    this.load();
  }

  /* template */ load() {
    this.hives.listHives().subscribe((hives) => this.records$.next(hives));
  }
}
