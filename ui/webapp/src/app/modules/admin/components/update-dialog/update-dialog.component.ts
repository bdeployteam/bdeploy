import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Version } from 'src/app/models/gen.dtos';
import { retryWithDelay } from 'src/app/modules/shared/utils/server.utils';
import { convert2String } from 'src/app/modules/shared/utils/version.utils';
import { ConfigService } from '../../../core/services/config.service';

export class UpdateDialogData {
  public waitFor: Promise<any>;
  public oldVersion: Version;
}

@Component({
  selector: 'app-update-dialog',
  templateUrl: './update-dialog.component.html',
  styleUrls: ['./update-dialog.component.css'],
})
export class UpdateDialogComponent implements OnInit {
  finished = false;
  waiting = false;
  error = false;

  versionAfterUpdate: Version;

  constructor(@Inject(MAT_DIALOG_DATA) public data: UpdateDialogData, private cfgSvc: ConfigService) {}

  ngOnInit() {
    this.data.waitFor.then(() => this.waitForServer());
  }

  async waitForServer(): Promise<any> {
    this.waiting = true;
    const observable = this.cfgSvc.tryGetBackendInfo();
    const dto = await observable.pipe(retryWithDelay()).toPromise();
    this.waiting = false;
    this.finished = true;
    if (!dto) {
      this.error = true;
    }
    this.versionAfterUpdate = dto.version;
  }

  isStillSameVersion() {
    return convert2String(this.versionAfterUpdate) === convert2String(this.data.oldVersion);
  }

  getVersionAfterUpdate() {
    return convert2String(this.versionAfterUpdate);
  }
}
