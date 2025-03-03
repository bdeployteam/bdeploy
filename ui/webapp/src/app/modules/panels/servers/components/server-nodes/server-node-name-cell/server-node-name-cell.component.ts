import { Component, inject, Input } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MinionRow } from '../server-nodes.component';
import { BdMicroIconButtonComponent } from '../../../../../core/components/bd-micro-icon-button/bd-micro-icon-button.component';
import { MatTooltip } from '@angular/material/tooltip';
import { BdDataColumn } from '../../../../../../models/data';
import {
  TableCellDisplay
} from '../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-server-node-name-cell',
    templateUrl: './server-node-name-cell.component.html',
    imports: [BdMicroIconButtonComponent, MatTooltip]
})
export class ServerNodeNameCellComponent implements TableCellDisplay<MinionRow> {
  private readonly snackbar = inject(MatSnackBar);

  @Input() record: MinionRow;
  @Input() column: BdDataColumn<MinionRow>;

  protected doCopy() {
    navigator.clipboard.writeText(this.record.name).then(
      () =>
        this.snackbar.open('Copied to clipboard successfully', null, {
          duration: 1000,
        }),
      () =>
        this.snackbar.open('Unable to write to clipboard.', null, {
          duration: 1000,
        }),
    );
  }
}
