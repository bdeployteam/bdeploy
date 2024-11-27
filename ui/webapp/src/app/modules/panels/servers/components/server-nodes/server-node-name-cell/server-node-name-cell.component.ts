import { Component, inject, Input } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MinionRow } from '../server-nodes.component';

@Component({
  selector: 'app-server-node-name-cell',
  templateUrl: './server-node-name-cell.component.html',
})
export class ServerNodeNameCellComponent {
  private readonly snackbar = inject(MatSnackBar);

  @Input() record: MinionRow;

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
