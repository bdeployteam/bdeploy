import { Component, OnInit, inject } from '@angular/core';
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
  isId: true,
};

@Component({
  selector: 'app-bhive',
  templateUrl: './bhive.component.html',
})
export class BHiveComponent implements OnInit {
  protected hives = inject(HiveService);

  protected records$ = new BehaviorSubject<string[]>(null);
  protected columns: BdDataColumn<string>[] = [COL_AVATAR, COL_ID];
  protected sort: Sort = { active: 'id', direction: 'asc' };

  protected getRecordRoute = (row: string) => {
    return ['', { outlets: { panel: ['panels', 'admin', 'bhive', row] } }];
  };

  ngOnInit() {
    this.load();
  }

  protected load() {
    this.hives.listHives().subscribe((hives) => this.records$.next(hives));
  }
}
