import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-bd-data-icon-cell',
  templateUrl: './bd-data-icon-cell.component.html',
  styleUrls: ['./bd-data-icon-cell.component.css'],
})
export class BdDataIconCellComponent<T> implements OnInit {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;

  constructor() {}

  ngOnInit(): void {}
}
