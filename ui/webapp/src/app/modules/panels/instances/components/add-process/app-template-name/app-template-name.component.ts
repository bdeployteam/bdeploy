import { Component, Input, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { AppRow } from '../add-process.component';

@Component({
  selector: 'app-app-template-name',
  templateUrl: './app-template-name.component.html',
  styleUrls: ['./app-template-name.component.css'],
})
export class AppTemplateNameComponent implements OnInit {
  @Input() record: AppRow;
  @Input() column: BdDataColumn<AppRow>;

  constructor() {}

  ngOnInit(): void {}
}
