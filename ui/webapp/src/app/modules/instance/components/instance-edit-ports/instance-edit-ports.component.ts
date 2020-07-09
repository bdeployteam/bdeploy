import { AfterViewInit, Component, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { cloneDeep, isEqual } from 'lodash';
import { Observable, of } from 'rxjs';
import { ApplicationConfiguration, InstanceNodeConfigurationDto, MinionDto, ParameterConfiguration, ParameterDescriptor, ParameterType } from 'src/app/models/gen.dtos';
import { ProcessConfigDto } from 'src/app/models/process.model';
import { MessageBoxMode } from 'src/app/modules/shared/components/messagebox/messagebox.component';
import { DownloadService } from 'src/app/modules/shared/services/download.service';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';
import { ApplicationService } from '../../services/application.service';
import { InstanceShiftPortsComponent, ShiftableParameter } from '../instance-shift-ports/instance-shift-ports.component';

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
  @Input() instanceId: string;
  @Input() processConfig: ProcessConfigDto;
  @Input() minionConfigs: { [ minionName: string ]: MinionDto };

  @Output() validationStateChanged = new EventEmitter<any>();

  @ViewChild('portsForm') portsForm: NgForm;

  clonedProcessConfig: ProcessConfigDto;
  portsPerApp: {[key: string]: ServerPortParameter[] } = {};
  appConfigs: {[key: string]: ApplicationConfiguration} = {};
  hasAnyPorts = false;

  constructor(private messageBoxService: MessageboxService, private dlService: DownloadService, private dialog: MatDialog, private appSvc: ApplicationService) { }

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
            this.appConfigs[app.uid] = app;
            this.portsPerApp[app.uid].push({
              paramCfg: param,
              paramDesc: paramDesc
            });
            this.hasAnyPorts = true;
          }
        }
      }
    }
  }

  ngAfterViewInit(): void {
    // this is required to run asynchronously to avoid changes to the model while updating the view.
    setTimeout(() => this.validationStateChanged.emit(false), 0);
  }

  onChange(param: ServerPortParameter, value: string) {
    param.paramCfg.value = value;
    param.paramCfg.preRendered = this.appSvc.preRenderParameter(param.paramDesc, value);
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

  shiftPorts() {
    const shiftable: ShiftableParameter[] = [];
    for (const app of Object.keys(this.portsPerApp)) {
      for (const spp of this.portsPerApp[app]) {
        shiftable.push({
          applicationUid: app,
          applicationName: this.appConfigs[app].name,
          cfg: spp.paramCfg,
          desc: spp.paramDesc,
          client: spp.paramDesc.type === ParameterType.CLIENT_PORT,
          selected: true
        });
      }
    }

    this.dialog.open(InstanceShiftPortsComponent, {
      width: '800px',
      data: shiftable,
    }).afterClosed().subscribe(r => {
      if (r) {
        for (const shift of shiftable) {
          if (shift.selected) {
            shift.cfg.value = (Number(shift.cfg.value) + Number(r)).toString();
            shift.cfg.preRendered = this.appSvc.preRenderParameter(shift.desc, shift.cfg.value);
            this.portsForm.controls[shift.applicationUid + '-' + shift.cfg.uid].markAsTouched();
            this.portsForm.controls[shift.applicationUid + '-' + shift.cfg.uid].updateValueAndValidity();
          }
        }
        this.portsForm.form.updateValueAndValidity();
        setTimeout(() => this.validationStateChanged.emit(this.portsForm.valid), 0);
      }
    });
  }

  exportCsv() {
    let csv = 'Application,Name,Description,Port';
    for (const app of Object.keys(this.portsPerApp)) {
      csv += '\n' + this.portsPerApp[app].filter(spp => spp.paramDesc.type === ParameterType.SERVER_PORT).map(spp => {
        return [ this.appConfigs[app].name, spp.paramDesc.name, spp.paramDesc.longDescription, spp.paramCfg.value ];
      }).map(r => r.map(e => `"${e}"`).join(',')).join('\n');
    }

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    this.dlService.downloadBlob('ports-' + this.instanceGroup + '-' + this.instanceId + '.csv', blob);
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
