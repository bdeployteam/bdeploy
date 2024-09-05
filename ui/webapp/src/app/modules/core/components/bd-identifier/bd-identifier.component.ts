import { Component, inject, Input } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'app-bd-identifier',
  templateUrl: './bd-identifier.component.html',
})
export class BdIdentifierComponent {
  @Input() id: string;
  @Input() showCopyButton: boolean = false;

  private readonly snackbar = inject(MatSnackBar);

  protected doCopy() {
    navigator.clipboard.writeText(this.id).then(
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
