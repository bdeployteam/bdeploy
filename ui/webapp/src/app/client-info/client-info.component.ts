import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ApplicationGroup } from '../models/application.model';
import { EventWithCallback } from '../models/event';
import { ApplicationConfiguration } from '../models/gen.dtos';

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

  @Output() downloadClickAndStartEvent = new EventEmitter<ApplicationConfiguration>();
  @Output() downloadInstallerEvent = new EventEmitter<EventWithCallback<ApplicationConfiguration>>();

  downloading = false;

  constructor() {}

  ngOnInit() {}

  getAppOs() {
    return ApplicationGroup.getAppOs(this.appConfig.application);
  }

  downloadClickAndStart() {
    this.downloadClickAndStartEvent.emit(this.appConfig);
  }

  downloadInstaller() {
    this.downloading = true;
    this.downloadInstallerEvent.emit(new EventWithCallback(this.appConfig, () => (this.downloading = false)));
  }
}
