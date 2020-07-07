import { AfterViewInit, Component, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { cloneDeep, isEqual } from 'lodash';
import { Observable, of } from 'rxjs';
import { ApplicationConfiguration, InstanceNodeConfigurationDto, MinionDto, ParameterConfiguration, ParameterDescriptor, ParameterType } from 'src/app/models/gen.dtos';
import { ProcessConfigDto } from 'src/app/models/process.model';
import { MessageBoxMode } from 'src/app/modules/shared/components/messagebox/messagebox.component';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';

interface ServerPortParameter {
  paramCfg: ParameterConfiguration;
  paramDesc: ParameterDescriptor;
}

@Component({
  selector: 'app-instance-edit-ports',
  templateUrl: './instance-edit-ports.component.html',
  styleUrls: ['./instance-edit-ports.component.css']
})
export class InstanceEditPortsComponent implements OnInit, AfterViewInit {

  @Input() instanceGroup: string;
  @Input() processConfig: ProcessConfigDto;
  @Input() minionConfigs: { [ minionName: string ]: MinionDto };

  @Output() validationStateChanged = new EventEmitter<any>();

  @ViewChild('portsForm') portsForm: NgForm;

  clonedProcessConfig: ProcessConfigDto;
  portsPerApp: {[key: string]: ServerPortParameter[] } = {};

  constructor(private messageBoxService: MessageboxService) { }

  ngOnInit(): void {
    this.clonedProcessConfig = cloneDeep(this.processConfig);

    for (const node of this.processConfig.nodeList.nodeConfigDtos) {
      for (const app of node.nodeConfiguration.applications) {
        for (const param of app.start.parameters) {
          const appDesc = this.processConfig.nodeList.applications[app.application.name];
          const paramDesc = appDesc.startCommand.parameters.find(p => p.uid === param.uid);

          if (paramDesc.type === ParameterType.SERVER_PORT || paramDesc.type === ParameterType.CLIENT_PORT) {
            if (!this.portsPerApp[app.uid]) {
              this.portsPerApp[app.uid] = [];
            }
            this.portsPerApp[app.uid].push({
              paramCfg: param,
              paramDesc: paramDesc
            });
          }
        }
      }
    }
  }

  ngAfterViewInit(): void {
    // this is required to run asynchronously to avoid changes to the model while updating the view.
    setTimeout(() => this.validationStateChanged.emit(false), 0);
  }

  onChange(cfg: ParameterConfiguration, value: string) {
    cfg.value = value;
    this.validationStateChanged.emit(this.portsForm.valid);
  }

  hasPorts(app: ApplicationConfiguration, node: InstanceNodeConfigurationDto): boolean {
    if (node.nodeName === '__ClientApplications') {
      return false;
    }

    return this.portsPerApp[app.uid]?.length > 0;
  }

  getPortParams(app: ApplicationConfiguration) {
    return this.portsPerApp[app.uid];
  }

  /**
   * Asks the user whether or not to discard changes if dirty.
   */
  public canDeactivate(): Observable<boolean> {
    if (!this.isDirty()) {
      return of(true);
    }
    return this.messageBoxService.open({
      title: 'Unsaved changes',
      message: 'Ports have been modified. Close without saving?',
      mode: MessageBoxMode.CONFIRM_WARNING,
    });
  }

  /**
   * Returns whether or not the current application config is valid.
   */
  public isValid() {
    return this.portsForm.valid;
  }

  /**
   * Returns whether or not there are local changes in this component
   */
  public isDirty() {
    return !isEqual(this.clonedProcessConfig, this.processConfig);
  }

}
