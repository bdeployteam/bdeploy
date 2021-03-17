import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { isEqual } from 'lodash-es';
import { tap } from 'rxjs/operators';
import { retryWithDelay } from 'src/app/modules/core/utils/server.utils';
import { ManagedServersService } from 'src/app/modules/legacy/servers/services/managed-servers.service';
import { MinionUpdateDto } from '../../../../../models/gen.dtos';
import { areUpdatesAvailable, isTransferDone, isUpdateFailed, isUpdateInProgress, isUpdateSuccess, UpdateStatus } from '../../../../../models/update.model';
import { AuthenticationService } from '../../../../core/services/authentication.service';

@Component({
  selector: 'app-managed-server-update',
  templateUrl: './managed-server-update.component.html',
  styleUrls: ['./managed-server-update.component.css'],
})
export class ManagedServerUpdateComponent implements OnInit {
  @Input()
  serverName: string;

  @Input()
  instanceGroupName: string;

  @Input()
  updateDto: MinionUpdateDto;

  @Output()
  updateEvent = new EventEmitter<UpdateStatus>();

  @Input()
  showClose = true;

  closed = false;
  updateStatus = UpdateStatus.UPDATES_AVAILABLE;
  updateStatusText = '';

  constructor(public authService: AuthenticationService, private managedServers: ManagedServersService) {}

  ngOnInit() {}

  async transferUpdates() {
    this.doTransferUpdates(true);
  }

  async transferAndInstallUpdates() {
    if (this.arePackagesMissing()) {
      await this.doTransferUpdates(false);
    }

    this.setStateAndNotify(UpdateStatus.INSTALL);
    this.updateStatusText = 'Installing update on managed server...';
    await this.managedServers.installUpdate(this.instanceGroupName, this.serverName, this.updateDto).toPromise();

    // Wait for some time so that the server is back up
    this.setStateAndNotify(UpdateStatus.RESTART);
    this.updateStatusText = 'Waiting for the master to come back online...';
    const version$ = this.managedServers.ping(this.instanceGroupName, this.serverName).pipe(
      tap((v) => {
        if (!isEqual(v, this.updateDto.updateVersion)) {
          throw new Error('Got unexpected version from server');
        }
      }),
      retryWithDelay()
    );
    const newVersion = await version$.toPromise();

    // Notify about the outcome
    if (newVersion !== null) {
      this.setStateAndNotify(UpdateStatus.SUCCESS);
    } else {
      this.setStateAndNotify(UpdateStatus.FAILED);
    }
  }

  async doTransferUpdates(notifyDone: boolean) {
    this.setStateAndNotify(UpdateStatus.TRANSFER);
    this.updateStatusText = 'Uploading software update package to managed server.';
    await this.managedServers.transferUpdate(this.instanceGroupName, this.serverName, this.updateDto).toPromise();
    if (notifyDone) {
      this.setStateAndNotify(UpdateStatus.TRANSFER_DONE);
    }
  }

  setStateAndNotify(updateStatus: UpdateStatus) {
    this.updateStatus = updateStatus;
    this.updateEvent.next(updateStatus);
  }

  arePackagesMissing() {
    if (isTransferDone(this.updateStatus)) {
      return false;
    }
    return this.updateDto && this.updateDto.packagesToTransfer && this.updateDto.packagesToTransfer.length > 0;
  }

  showUpdateHint() {
    return areUpdatesAvailable(this.updateStatus);
  }

  showInProgressHint() {
    return isUpdateInProgress(this.updateStatus);
  }

  showSuccessHint() {
    return isUpdateSuccess(this.updateStatus);
  }

  showFailedHint() {
    return isUpdateFailed(this.updateStatus);
  }

  isSnapshot() {
    return this.updateDto.packagesToInstall.some((key) => key.name.includes('snapshot'));
  }

  canApply() {
    return this.authService.isScopedAdmin(this.instanceGroupName);
  }
}
