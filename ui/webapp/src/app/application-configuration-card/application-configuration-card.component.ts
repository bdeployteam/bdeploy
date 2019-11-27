import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { EventWithCallback } from '../models/event';
import { ApplicationConfiguration, InstanceVersionDto, OperatingSystem } from '../models/gen.dtos';
import { MessageBoxMode } from '../modules/shared/components/messagebox/messagebox.component';
import { MessageboxService } from '../modules/shared/services/messagebox.service';
import { getAppOs } from '../modules/shared/utils/manifest.utils';
import { ApplicationService } from '../services/application.service';
import { LauncherService } from '../services/launcher.service';

@Component({
  selector: 'app-application-configuration-card',
  templateUrl: './application-configuration-card.component.html',
  styleUrls: ['./application-configuration-card.component.css'],
})
export class ApplicationConfigurationCardComponent implements OnInit {
  @Input() public instanceVersion: InstanceVersionDto;
  @Input() public appConfig: ApplicationConfiguration;
  @Input() public activatedTag: string;
  @Input() productMissing: boolean;
  @Input() public isForeign = false;
  @Input() public isReadonly = false;
  @Input() public isClient = false;
  @Input() public isInstanceDirty = false;

  @Output() editEvent = new EventEmitter<ApplicationConfiguration>();
  @Output() removeEvent = new EventEmitter<boolean>();
  @Output() downloadClickAndStartEvent = new EventEmitter<ApplicationConfiguration>();
  @Output() downloadInstallerEvent = new EventEmitter<EventWithCallback<ApplicationConfiguration>>();

  appOs: OperatingSystem;
  downloading = false;

  constructor(
    private mbService: MessageboxService,
    private appService: ApplicationService,
    private launcherService: LauncherService,
  ) {}

  ngOnInit() {
    this.appOs = getAppOs(this.appConfig.application);
  }

  isActive() {
    return this.activatedTag === this.instanceVersion.key.tag;
  }

  onEdit() {
    this.editEvent.emit(this.appConfig);
  }

  onDelete(): void {
    const resultPromise = this.mbService.open({
      title: 'Delete ' + this.appConfig.name,
      message: 'Deleting a process <b>cannot be undone</b>.',
      mode: MessageBoxMode.CONFIRM_WARNING,
    });
    resultPromise.subscribe(result => {
      if (result !== true) {
        return;
      }
      this.removeEvent.emit(true);
    });
  }

  getCardStyle() {
    const styles = [];
    if (!this.isValid()) {
      styles.push('app-config-invalid');
    } else if (this.isDirty()) {
      styles.push('app-config-modified');
    }
    if (this.isMissing()) {
      styles.push('app-config-missing');
    }
    if (this.isForeign) {
      styles.push('app-config-foreign');
    }
    return styles;
  }

  isDirty() {
    return this.appService.isDirty(this.appConfig.uid);
  }

  isValid() {
    return this.appService.isValid(this.appConfig.uid);
  }

  isMissing() {
    return this.appService.isMissing(this.appConfig.application);
  }

  hasLauncher() {
    return this.launcherService.hasLauncherForOs(this.appOs);
  }

  downloadClickAndStart() {
    this.downloadClickAndStartEvent.emit(this.appConfig);
  }

  downloadInstaller() {
    this.downloading = true;
    this.downloadInstallerEvent.emit(new EventWithCallback(this.appConfig, () => (this.downloading = false)));
  }
}
