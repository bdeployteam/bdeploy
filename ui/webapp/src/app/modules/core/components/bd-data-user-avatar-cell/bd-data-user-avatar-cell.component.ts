import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-bd-data-user-avatar-cell',
  templateUrl: './bd-data-user-avatar-cell.component.html',
  styleUrls: ['./bd-data-user-avatar-cell.component.css'],
})
export class BdDataUserAvatarCellComponent<T> implements OnInit {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;

  constructor() {}

  ngOnInit(): void {}
}
