import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { MessageboxComponent, MessageBoxData } from '../messagebox/messagebox.component';

@Injectable({
  providedIn: 'root',
})
export class MessageboxService {
  constructor(private dialog: MatDialog) {}

  public async openAsync(data: MessageBoxData): Promise<boolean> {
    return this.open(data).toPromise();
  }

  public open(data: MessageBoxData): Observable<boolean> {
    return this.dialog
      .open(MessageboxComponent, {
        minWidth: '300px',
        maxWidth: '800px',
        data: data,
      })
      .afterClosed()
      .pipe(
        map(r => {
          if (r === undefined || r === null) {
            return false;
          }
          return r;
        }),
      );
  }
}
