import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { BdPopupDirective } from '../bd-popup/bd-popup.directive';
import { MatCard } from '@angular/material/card';

@Component({
    selector: 'app-bd-data-popover-cell',
    templateUrl: './bd-data-popover-cell.component.html',
    imports: [BdPopupDirective, MatCard]
})
export class BdDataPopoverCellComponent<T> {
  @Input() record: T;
  @Input() column: BdDataColumn<T>;
}
