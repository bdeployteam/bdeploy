import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { format } from 'date-fns';
import { InstanceConfiguration, ManagedMasterDto, MinionDto, MinionStatusDto, Version } from '../../../../models/gen.dtos';
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

  columnsToDisplay = ['minion', 'url', 'version', 'os', 'status'];
  dataSource: MatTableDataSource<MinionTableRow>;

  constructor(private mbService: MessageboxService, private managedServers: ManagedServersService) {}

  ngOnInit() {
    this.load();
  }

  async load() {
    this.managedServers.getInstancesForManagedServer(this.instanceGroupName, this.server.name).subscribe(r => {
      this.instances = r;
    });
    const minions = await this.managedServers
      .minionsConfigOfManagedServer(this.instanceGroupName, this.server.name)
      .toPromise();

    const arr: MinionTableRow[] = [];
    for (const key of Object.keys(minions)) {
      arr.push({ key: key, config: minions[key] });
    }
    this.loading = false;
    this.dataSource = new MatTableDataSource<MinionTableRow>(arr);
  }

  async doDelete() {
    let doIt = false;
    if (this.instances.length > 0) {
      doIt = await this.mbService.openAsync({
        title: 'Delete Managed Server',
        message: `Are you sure you want to delete the selected managed server from the central server? This will delete <b>${this.instances.length}</b> instances from the central server as well (but not from the managed server).`,
        mode: MessageBoxMode.CONFIRM_WARNING,
      });
    } else {
      doIt = await this.mbService.openAsync({
        title: 'Delete Managed Server',
        message: 'Are you sure you want to delete the selected managed server from the central server?',
        mode: MessageBoxMode.CONFIRM,
      });
    }
    if (doIt) {
      await this.managedServers.deleteManagedServer(this.instanceGroupName, this.server.name).toPromise();
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
    if (!version) {
      return 'Unknown';
    }
    return version.major + '.' + version.minor + '.' + version.micro + '.' + version.qualifier;
  }

  getStatusIcon(minion: string) {
    if (!this.minionState) {
      return 'error';
    }
    const status = this.minionState[minion];
    if (status.offline) {
      return 'error';
    }
    return 'favorite';
  }

  getStatusTooltip(minion: string) {
    if (!this.minionState) {
      return 'Cannot communicate with remote master. Check if the minion is online';
    }
    const status = this.minionState[minion];
    if (status.offline) {
      return 'Cannot communicate with minion. Details: ' + status.infoText;
    }
    return 'Minion is online.';
  }

  getStatusClass(minion: string) {
    const styles = ['icon'];
    if (!this.minionState) {
      styles.push('app-process-crash');
    }
    if (this.minionState && this.minionState[minion].offline) {
      styles.push('app-process-crash');
    }
    if (this.minionState && !this.minionState[minion].offline) {
      styles.push('app-process-running');
    }
    return styles;
  }

  async doSync() {
    this.loading = true;
    this.synchronized = true;

    try {
      this.server = await this.managedServers.synchronize(this.instanceGroupName, this.server.name).toPromise();
      this.minionState = await this.managedServers.minionsStateOfManagedServer(this.instanceGroupName, this.server.name).toPromise();
    } catch {
      this.mbService.open({
        title: 'Synchronization Error',
        message: 'Synchronization failed. The remote master server might be offline.',
        mode: MessageBoxMode.ERROR,
      });
      this.minionState = null;
    }

    this.load();
  }
}
