import { Component, Input, OnInit } from '@angular/core';

@Component({
  selector: 'app-bd-no-data',
  templateUrl: './bd-no-data.component.html',
  styleUrls: ['./bd-no-data.component.css'],
})
export class BdNoDataComponent implements OnInit {
  @Input() header: string;

  constructor() {}

  ngOnInit(): void {}
}
