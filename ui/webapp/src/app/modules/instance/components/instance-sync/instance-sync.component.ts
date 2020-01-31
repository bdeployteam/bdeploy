import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { format } from 'date-fns';
import { ManagedMasterDto, MinionMode } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { ManagedServersService } from 'src/app/modules/servers/services/managed-servers.service';
import { MessageBoxMode } from 'src/app/modules/shared/components/messagebox/messagebox.component';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';

@Component({
  selector: 'app-instance-sync',
  templateUrl: './instance-sync.component.html',
  styleUrls: ['./instance-sync.component.css']
})
export class InstanceSyncComponent implements OnChanges {

  @Input()
  instanceGroup: string;

  @Input()
  instance: string;

  @Input()
  tag: string;

  @Output()
  syncEvent = new EventEmitter<any>();

  @Output()
  stateUpdateEvent = new EventEmitter<any>();

  server: ManagedMasterDto;

  constructor(private config: ConfigService, private managedServers: ManagedServersService, private messageBoxService: MessageboxService, public authService: AuthenticationService) { }

  async ngOnChanges() {
    if (this.config.config.mode !== MinionMode.CENTRAL) {
      return; // only on central
    }
    if (this.instanceGroup && this.instance) {
      this.server = await this.managedServers.getServerForInstance(this.instanceGroup, this.instance, this.tag).toPromise();
    }
    if (this.server) {
      this.stateUpdateEvent.emit(null);
    }
  }

  public isInSync(): boolean {
    if (!this.server) {
      return false;
    }
    const currentTime = new Date().getTime();
    return (currentTime - this.server.lastSync) <= (1_000 * 60 * 15);
  }

  public getServerName() {
    if (!this.server) {
      return null;
    }
    return this.server.hostName;
  }

  async doSyncCentral() {
    try {
      await this.managedServers.synchronize(this.instanceGroup, this.server.hostName).toPromise();
      this.ngOnChanges();
      this.syncEvent.emit(null);
    } catch (e) {
      this.messageBoxService.open({
        title: 'Synchronization Error',
        message: 'Synchronization failed. The remote master server might be offline.',
        mode: MessageBoxMode.ERROR});
    }
  }

  getDate(x: number) {
    return format(new Date(x), 'dd.MM.yyyy HH:mm');
  }
}
