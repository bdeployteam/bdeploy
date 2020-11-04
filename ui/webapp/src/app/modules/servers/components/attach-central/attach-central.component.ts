import { Location } from '@angular/common';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MatStep, MatStepper } from '@angular/material/stepper';
import ReconnectingWebSocket from 'reconnecting-websocket';
import { RoutingHistoryService } from 'src/app/modules/core/services/routing-history.service';
import { InstanceGroupConfiguration, ManagedMasterDto } from '../../../../models/gen.dtos';
import { ErrorMessage, LoggingService } from '../../../core/services/logging.service';
import { InstanceGroupService } from '../../../instance-group/services/instance-group.service';
import { DownloadService } from '../../../shared/services/download.service';
import { RemoteEventsService } from '../../../shared/services/remote-events.service';
import { ManagedServersService } from '../../services/managed-servers.service';

@Component({
  selector: 'app-attach-central',
  templateUrl: './attach-central.component.html',
  styleUrls: ['./attach-central.component.css'],
})
export class AttachCentralComponent implements OnInit, OnDestroy {
  // inter-browser support only works with text/plain...
  static readonly ATTACH_MIME_TYPE = 'text/plain';

  private log = this.logging.getLogger('AttachCentralComponent');
  attachPayload: ManagedMasterDto;
  ws: ReconnectingWebSocket;
  remoteAttached: InstanceGroupConfiguration;
  manualLoading = false;

  @ViewChild(MatStepper, { static: true })
  stepper: MatStepper;

  @ViewChild('doneStep', { static: true })
  doneStep: MatStep;

  constructor(
    public location: Location,
    private eventService: RemoteEventsService,
    private logging: LoggingService,
    private igService: InstanceGroupService,
    private dlService: DownloadService,
    private managedServers: ManagedServersService,
    public routingHistoryService: RoutingHistoryService
  ) {}

  ngOnInit() {
    this.managedServers.getManagedMasterInfo().subscribe((i) => (this.attachPayload = i));

    this.ws = this.eventService.createAttachEventsWebSocket();
    this.ws.addEventListener('error', (err) => {
      this.log.error(new ErrorMessage('Error waiting for attach events', err));
    });
    this.ws.addEventListener('message', (e) => this.onRemoteAttach(e));
  }

  ngOnDestroy() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  onRemoteAttach(e: MessageEvent) {
    const blob = e.data as Blob;
    const r = new FileReader();
    r.onload = () => {
      const groupName = r.result as string;
      this.igService.getInstanceGroup(groupName).subscribe((res) => {
        this.remoteAttached = res;
        this.stepper.selected = this.doneStep;
      });
    };
    r.readAsText(blob);
  }

  onDragStart($event) {
    $event.dataTransfer.effectAllowed = 'link';
    $event.dataTransfer.setData(AttachCentralComponent.ATTACH_MIME_TYPE, JSON.stringify(this.attachPayload));
  }

  downloadManualJson() {
    this.dlService.downloadJson('server-' + this.attachPayload.hostName + '.json', this.attachPayload);
  }

  async onDrop($event: DragEvent) {
    $event.preventDefault();

    let data;
    if ($event.dataTransfer.files.length > 0) {
      await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = (e) => {
          data = reader.result.toString();
          resolve();
        };
        reader.onerror = (e) => reject();
        reader.readAsText($event.dataTransfer.files[0]);
      });
    } else if ($event.dataTransfer.types.includes(AttachCentralComponent.ATTACH_MIME_TYPE)) {
      data = $event.dataTransfer.getData(AttachCentralComponent.ATTACH_MIME_TYPE);
    }

    this.manualLoading = true;

    this.managedServers.manualAttachCentral(data).subscribe((group) => {
      this.igService.getInstanceGroup(group).subscribe((r) => {
        this.remoteAttached = r;
        this.stepper.selected = this.doneStep;
      });
    });
  }

  onOver($event: DragEvent) {
    // need to cancel the event and return false to ALLOW drop.
    if ($event.preventDefault) {
      $event.preventDefault();
    }

    return false;
  }
}
