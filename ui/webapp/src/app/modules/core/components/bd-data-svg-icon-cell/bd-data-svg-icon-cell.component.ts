import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-bd-data-svg-icon-cell',
  templateUrl: './bd-data-svg-icon-cell.component.html',
  styleUrls: ['./bd-data-svg-icon-cell.component.css'],
})
export class BdDataSvgIconCellComponent<T> implements OnInit {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;

  constructor() {}

  ngOnInit(): void {}
}
