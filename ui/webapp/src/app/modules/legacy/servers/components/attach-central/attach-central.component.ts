import { Location } from '@angular/common';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MatStep, MatStepper } from '@angular/material/stepper';
import { Subscription } from 'rxjs';
import { EMPTY_SCOPE, ObjectChangesService } from 'src/app/modules/core/services/object-changes.service';
import { RoutingHistoryService } from 'src/app/modules/legacy/core/services/routing-history.service';
import {
  InstanceGroupConfiguration,
  ManagedMasterDto,
  ObjectChangeDetails,
  ObjectChangeType,
} from '../../../../../models/gen.dtos';
import { DownloadService } from '../../../../core/services/download.service';
import { LoggingService } from '../../../../core/services/logging.service';
import { InstanceGroupService } from '../../../instance-group/services/instance-group.service';
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
  remoteAttached: InstanceGroupConfiguration;
  manualLoading = false;

  @ViewChild(MatStepper, { static: true })
  stepper: MatStepper;

  @ViewChild('doneStep', { static: true })
  doneStep: MatStep;

  subscription: Subscription;

  constructor(
    public location: Location,
    private logging: LoggingService,
    private igService: InstanceGroupService,
    private dlService: DownloadService,
    private managedServers: ManagedServersService,
    public routingHistoryService: RoutingHistoryService,
    private changes: ObjectChangesService
  ) {}

  ngOnInit() {
    this.managedServers.getManagedMasterInfo().subscribe((i) => (this.attachPayload = i));

    this.subscription = this.changes.subscribe(ObjectChangeType.MANAGED_MASTER_ATTACH, EMPTY_SCOPE, (c) =>
      this.onRemoteAttach(c.details[ObjectChangeDetails.CHANGE_HINT])
    );
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  onRemoteAttach(groupName: string) {
    this.igService.getInstanceGroup(groupName).subscribe((res) => {
      this.remoteAttached = res;
      this.stepper.selected = this.doneStep;
    });
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
          resolve(data);
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
