import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-permission-level',
  templateUrl: './permission-level.component.html',
  styleUrls: ['./permission-level.component.css'],
})
export class PermissionLevelComponent<T> implements OnInit {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;

  constructor() {}

  ngOnInit(): void {}
}
