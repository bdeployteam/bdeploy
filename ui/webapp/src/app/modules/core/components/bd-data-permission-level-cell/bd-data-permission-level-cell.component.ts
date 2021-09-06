import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-bd-data-permission-level-cell',
  templateUrl: './bd-data-permission-level-cell.component.html',
  styleUrls: ['./bd-data-permission-level-cell.component.css'],
})
export class BdDataPermissionLevelCellComponent<T> implements OnInit {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;

  constructor() {}

  ngOnInit(): void {}
}
