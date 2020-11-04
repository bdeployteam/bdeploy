import { Injectable } from '@angular/core';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { delay, retryWhen } from 'rxjs/operators';
import { ConnectionLostComponent } from '../components/connection-lost/connection-lost.component';
import { ConfigService } from './config.service';

@Injectable({
  providedIn: 'root',
})
export class SystemService {
  private recovering = false;

  constructor(private configService: ConfigService, private dialog: MatDialog) {}

  public backendUnreachable(): void {
    if (!this.recovering) {
      this.recovering = true;

      const config: MatDialogConfig = {
        minWidth: '300px',
        maxWidth: '800px',
        disableClose: true,
        data: {},
      };

      const dialogRef = this.dialog.open(ConnectionLostComponent, config);

      this.configService
        .tryGetBackendInfo()
        .pipe(retryWhen((errors) => errors.pipe(delay(2000))))
        .subscribe((r) => {
          dialogRef.close();
          this.recovering = false;
        });
    }
  }
}
