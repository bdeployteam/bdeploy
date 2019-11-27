import { Component, Inject, OnInit } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { of } from 'rxjs';
import { catchError, delay, retryWhen, scan, takeWhile } from 'rxjs/operators';
import { ConfigService } from '../../../core/services/config.service';

export class UpdateDialogData {
  public waitFor: Promise<any>;
}

@Component({
  selector: 'app-update-dialog',
  templateUrl: './update-dialog.component.html',
  styleUrls: ['./update-dialog.component.css']
})
export class UpdateDialogComponent implements OnInit {

  finished = false;
  waiting = false;
  error = false;
  versionAfterUpdate: string;

  constructor(private dialogRef: MatDialogRef<UpdateDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: UpdateDialogData, private cfgSvc: ConfigService) { }

  ngOnInit() {
    this.data.waitFor.then(() => this.waitForServer());
  }

  private waitForServer(): Promise<any> {
    this.waiting = true;
    return this.cfgSvc.tryGetBackendVersion().pipe(
      retryWhen(errors => errors.pipe(
        scan(acc => acc + 1, 0),
        takeWhile(acc => acc < 60),
        delay(1000),
        catchError(e => { console.log('eeek'); return of(null); })
      )
    )).toPromise().then(r => {
      this.waiting = false;
      this.finished = true;
      if (!r) {
        this.error = true;
      }
      this.versionAfterUpdate = r.version;
    });
  }

}
