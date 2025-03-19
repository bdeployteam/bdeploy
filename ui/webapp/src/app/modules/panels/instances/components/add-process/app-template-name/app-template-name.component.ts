import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { AppRow } from '../add-process.component';
import { MatTooltip } from '@angular/material/tooltip';
import { CellComponent } from '../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-app-template-name',
    templateUrl: './app-template-name.component.html',
    styleUrls: ['./app-template-name.component.css'],
    imports: [MatTooltip]
})
export class AppTemplateNameComponent implements CellComponent<AppRow, string> {
  @Input() record: AppRow;
  @Input() column: BdDataColumn<AppRow, string>;
}
