import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { AppRow } from '../add-process.component';

@Component({
    selector: 'app-app-template-name',
    templateUrl: './app-template-name.component.html',
    styleUrls: ['./app-template-name.component.css'],
    standalone: false
})
export class AppTemplateNameComponent {
  @Input() record: AppRow;
  @Input() column: BdDataColumn<AppRow>;
}
