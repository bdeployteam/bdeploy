import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-bd-data-sync-cell',
  templateUrl: './bd-data-sync-cell.component.html',
  styleUrls: ['./bd-data-sync-cell.component.css'],
})
export class BdDataSyncCellComponent<T> implements OnInit {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;

  constructor() {}

  ngOnInit(): void {}
}
