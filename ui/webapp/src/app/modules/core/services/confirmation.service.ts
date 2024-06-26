import { ComponentType } from '@angular/cdk/portal';
import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { BdConfirmationComponent } from '../components/bd-confirmation/bd-confirmation.component';

@Injectable({
  providedIn: 'root',
})
export class ConfirmationService {
  constructor(private dialog: MatDialog) {}

  public confirm(header: string, message: string): Observable<boolean> {
    const dialogRef = this.dialog.open(BdConfirmationComponent, {
      disableClose: true,
      panelClass: 'bd-dialog-container',
      data: { header, message },
    });
    return dialogRef.afterClosed();
  }

  public prompt<T>(component: ComponentType<T>, data: unknown): Observable<boolean> {
    const dialogRef = this.dialog.open(component, {
      disableClose: true,
      panelClass: 'bd-dialog-container',
      data,
    });
    return dialogRef.afterClosed();
  }
}
