import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { MessageBoxMode } from '../messagebox/messagebox.component';
import { ApplicationGroup } from '../models/application.model';
import { ApplicationConfiguration, InstanceVersionDto, OperatingSystem } from '../models/gen.dtos';
import { ApplicationService } from '../services/application.service';
import { MessageboxService } from '../services/messagebox.service';

@Component({
  selector: 'app-application-configuration-card',
  templateUrl: './application-configuration-card.component.html',
  styleUrls: ['./application-configuration-card.component.css'],
})
export class ApplicationConfigurationCardComponent implements OnInit {
  @Input() public instanceVersion: InstanceVersionDto;
  @Input() public appConfig: ApplicationConfiguration;
  @Input() public activatedTag: string;
  @Input() public isForeign = false;
  @Input() public isReadonly = false;
  @Input() public isClient = false;

  @Output() editEvent = new EventEmitter<ApplicationConfiguration>();
  @Output() removeEvent = new EventEmitter<boolean>();
  @Output() downloadLauncherEvent = new EventEmitter<ApplicationConfiguration>();

  appOs: OperatingSystem;

  constructor(private mbService: MessageboxService, private appService: ApplicationService) {}

  ngOnInit() {
    this.appOs = ApplicationGroup.getAppOs(this.appConfig.application);
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
}
