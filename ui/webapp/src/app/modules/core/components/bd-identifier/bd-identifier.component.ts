import { Component, inject, Input } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NgClass } from '@angular/common';
import { MatTooltip } from '@angular/material/tooltip';
import { BdMicroIconButtonComponent } from '../bd-micro-icon-button/bd-micro-icon-button.component';

@Component({
    selector: 'app-bd-identifier',
    templateUrl: './bd-identifier.component.html',
    imports: [NgClass, MatTooltip, BdMicroIconButtonComponent]
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
