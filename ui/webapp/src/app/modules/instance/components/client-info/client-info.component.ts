import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { EventWithCallback } from '../../../../models/event';
import { ApplicationConfiguration, InstanceStateRecord } from '../../../../models/gen.dtos';
import { LauncherService } from '../../../shared/services/launcher.service';
import { getAppOs } from '../../../shared/utils/manifest.utils';

@Component({
  selector: 'app-client-info',
  templateUrl: './client-info.component.html',
  styleUrls: ['./client-info.component.css'],
})
export class ClientInfoComponent implements OnInit {
  @Input() instanceGroup: string;
  @Input() instanceId: string;
  @Input() instanceTag: string;
  @Input() appConfig: ApplicationConfiguration;
  @Input() deploymentState: InstanceStateRecord;

  @Output() downloadClickAndStartEvent = new EventEmitter<ApplicationConfiguration>();
  @Output() downloadInstallerEvent = new EventEmitter<EventWithCallback<ApplicationConfiguration>>();

  downloading = false;

  constructor(private launcherService: LauncherService) {}

  ngOnInit() {}

  isActiveVersion() {
    return this.instanceTag === this.deploymentState.activeTag;
  }

  getAppOs() {
    return getAppOs(this.appConfig.application);
  }

  downloadClickAndStart() {
    this.downloadClickAndStartEvent.emit(this.appConfig);
  }

  downloadInstaller() {
    this.downloading = true;
    this.downloadInstallerEvent.emit(new EventWithCallback(this.appConfig, () => (this.downloading = false)));
  }

  hasLauncher() {
    return this.launcherService.hasLauncherForOs(this.getAppOs());
  }
}
