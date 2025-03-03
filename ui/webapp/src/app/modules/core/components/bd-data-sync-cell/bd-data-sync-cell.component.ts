import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { BdServerSyncButtonComponent } from '../bd-server-sync-button/bd-server-sync-button.component';
import { ClickStopPropagationDirective } from '../../directives/click-stop-propagation.directive';
import { TableCellDisplay } from '../bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-bd-data-sync-cell',
    templateUrl: './bd-data-sync-cell.component.html',
    imports: [BdServerSyncButtonComponent, ClickStopPropagationDirective]
})
export class BdDataSyncCellComponent<T> implements TableCellDisplay<T>{
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
