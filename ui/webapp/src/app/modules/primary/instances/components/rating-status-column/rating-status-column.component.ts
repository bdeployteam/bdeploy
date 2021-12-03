import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';

@Component({
  selector: 'app-rating-status-column',
  templateUrl: './rating-status-column.component.html',
  styleUrls: ['./rating-status-column.component.css'],
})
export class RatingStatusColumnComponent<T> implements OnInit {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;

  constructor() {}

  ngOnInit(): void {}
}
