import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { format } from 'date-fns';
import { isUpdateFailed, isUpdateInProgress, isUpdateSuccess, UpdateStatus } from 'src/app/models/update.model';
import { convert2String } from 'src/app/modules/shared/utils/version.utils';
import { InstanceConfiguration, ManagedMasterDto, MinionDto, MinionStatusDto, MinionUpdateDto, Version } from '../../../../models/gen.dtos';
import { MessageBoxMode } from '../../../shared/components/messagebox/messagebox.component';
import { MessageboxService } from '../../../shared/services/messagebox.service';
import { ManagedServersService } from '../../services/managed-servers.service';

interface MinionTableRow {
  key: string;
  config: MinionDto;
}

@Component({
  selector: 'app-managed-server-detail',
  templateUrl: './managed-server-detail.component.html',
  styleUrls: ['./managed-server-detail.component.css'],
})
export class ManagedServerDetailComponent implements OnInit {
  @Input()
  public server: ManagedMasterDto;

  @Input()
  public instanceGroupName: string;

  @Output()
  public reload = new EventEmitter<any>();

  loading = true;
  synchronized = false;
  instances: InstanceConfiguration[];
  minionState: { [minionName: string]: MinionStatusDto };

  updateDto: MinionUpdateDto;
  updateStatus: UpdateStatus;

  columnsToDisplay = ['minion', 'url', 'version', 'os', 'status'];
  dataSource: MatTableDataSource<MinionTableRow>;

  constructor(private messageBoxService: MessageboxService, private managedServers: ManagedServersService) {}

  ngOnInit() {
    this.load();
  }

  async load() {
    this.managedServers.getInstancesForManagedServer(this.instanceGroupName, this.server.hostName).subscribe(r => {
      this.instances = r;
    });
    const minions = await this.managedServers
      .minionsConfigOfManagedServer(this.instanceGroupName, this.server.hostName)
      .toPromise();

    const arr: MinionTableRow[] = [];
    for (const key of Object.keys(minions)) {
      arr.push({ key: key, config: minions[key] });
    }
    this.loading = false;
    this.dataSource = new MatTableDataSource<MinionTableRow>(arr);
  }

  async delete() {
    let doIt = false;
    if (this.instances.length > 0) {
      doIt = await this.messageBoxService.openAsync({
        title: 'Delete Managed Server',
        message: `Are you sure you want to delete the selected managed server from the central server? This will delete <b>${this.instances.length}</b> instances from the central server as well (but not from the managed server).`,
        mode: MessageBoxMode.CONFIRM_WARNING,
      });
    } else {
      doIt = await this.messageBoxService.openAsync({
        title: 'Delete Managed Server',
        message: 'Are you sure you want to delete the selected managed server from the central server?',
        mode: MessageBoxMode.CONFIRM,
      });
    }
    if (doIt) {
      await this.managedServers.deleteManagedServer(this.instanceGroupName, this.server.hostName).toPromise();
      this.reload.emit();
    }
  }

  getDate(timeInMs: number) {
    if (timeInMs === 0) {
      return '-';
    }
    return format(new Date(timeInMs), 'dd.MM.yyyy HH:mm');
  }

  getVersion(version: Version) {
    return convert2String(version);
  }

  getStatusIcon(minion: string) {
    if (!this.minionState || this.isUpdateFailed()) {
      return 'error';
    }
    if (this.updateDto && this.updateDto.updateAvailable) {
      return 'system_update';
    }

    const status = this.minionState[minion];
    if (status.offline) {
      return 'error';
    }
    return 'favorite';
  }

  getStatusTooltip(minion: string) {
    if (!this.minionState || this.isUpdateFailed()) {
      return 'Cannot communicate with remote master. Check if the minion is online';
    }
    if (this.updateDto && this.updateDto.updateAvailable) {
      return 'Software update available';
    }
    const status = this.minionState[minion];
    if (status.offline) {
      return 'Cannot communicate with minion. Details: ' + status.infoText;
    }
    return 'Minion is online.';
  }

  getStatusClass(minion: string) {
    const styles = ['icon'];
    if (!this.minionState || this.isUpdateFailed()) {
      styles.push('app-process-crash');
      return styles;
    }
    if (this.updateDto && this.updateDto.updateAvailable) {
      styles.push('app-process-running');
      return styles;
    }
    if (this.minionState[minion].offline) {
      styles.push('app-process-crash');
    }
    if (!this.minionState[minion].offline) {
      styles.push('app-process-running');
    }
    return styles;
  }

  async synchronize() {
    this.loading = true;
    this.updateStatus = null;
    await this.doSynchronize();
    await this.load();
  }

  async doSynchronize() {
    try {
      this.server = await this.managedServers.synchronize(this.instanceGroupName, this.server.hostName).toPromise();
      this.minionState = await this.managedServers.minionsStateOfManagedServer(this.instanceGroupName, this.server.hostName).toPromise();
      this.updateDto = await this.managedServers.getUpdatesFor(this.instanceGroupName, this.server.hostName).toPromise();
      this.synchronized = true;
    } catch {
      this.minionState = null;
      this.updateDto = null;
      this.synchronized = false;
      this.messageBoxService.open({
        title: 'Synchronization Error',
        message: 'Synchronization failed. The remote master server might be offline.',
        mode: MessageBoxMode.ERROR,
      });
    }
  }

  showUpdateComponent() {
    if (this.updateDto && this.updateDto.updateAvailable) {
      return true;
    }
    if (this.updateStatus) {
      return true;
    }
    return false;
  }

  onUpdateEvent(updateState: UpdateStatus) {
    this.updateStatus = updateState;
    if (this.isUpdateSuccess()) {
      this.doSynchronize();
    }
    if (this.isUpdateFailed()) {
      this.synchronized = false;
      this.messageBoxService.open({
        title: 'Update Error',
        message: 'Failed to await server to come back online. Please check server logs.',
        mode: MessageBoxMode.ERROR,
      });
    }
  }

  isUpdateInProgress() {
    return this.updateStatus && isUpdateInProgress(this.updateStatus);
  }

  isUpdateSuccess() {
    return this.updateStatus && isUpdateSuccess(this.updateStatus);
  }

  isUpdateFailed() {
    return this.updateStatus && isUpdateFailed(this.updateStatus);
  }

}
