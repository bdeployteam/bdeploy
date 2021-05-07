import { AfterViewInit, Component, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { cloneDeep, isEqual } from 'lodash-es';
import { Observable, of } from 'rxjs';
import {
  ApplicationConfiguration,
  ApplicationDescriptor,
  InstanceNodeConfigurationDto,
  MinionDto,
  ParameterConfiguration,
  ParameterDescriptor,
  ParameterType,
} from 'src/app/models/gen.dtos';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { ProcessConfigDto } from 'src/app/modules/legacy/core/models/process.model';
import { MessageBoxMode } from 'src/app/modules/legacy/shared/components/messagebox/messagebox.component';
import { MessageboxService } from 'src/app/modules/legacy/shared/services/messagebox.service';
import { URLish } from 'src/app/modules/legacy/shared/utils/url.utils';
import { ApplicationService } from '../../services/application.service';
import { InstanceShiftPortsComponent, ShiftableParameter } from '../instance-shift-ports/instance-shift-ports.component';

interface ServerPortParameter {
  paramCfg: ParameterConfiguration;
  paramDesc: ParameterDescriptor;
  appCfg: ApplicationConfiguration;
  appDesc: ApplicationDescriptor;
}

@Component({
  selector: 'app-instance-edit-ports',
  templateUrl: './instance-edit-ports.component.html',
  styleUrls: ['./instance-edit-ports.component.css'],
})
export class InstanceEditPortsComponent implements OnInit, AfterViewInit {
  @Input() instanceGroup: string;
  @Input() instanceId: string;
  @Input() processConfig: ProcessConfigDto;
  @Input() minionConfigs: { [minionName: string]: MinionDto };

  @Output() validationStateChanged = new EventEmitter<any>();

  @ViewChild('portsForm') portsForm: NgForm;

  clonedProcessConfig: ProcessConfigDto;
  portsPerApp: { [key: string]: ServerPortParameter[] } = {};
  appConfigs: { [key: string]: ApplicationConfiguration } = {};
  hasAnyPorts = false;

  constructor(
    private messageBoxService: MessageboxService,
    private dlService: DownloadService,
    private dialog: MatDialog,
    private appSvc: ApplicationService
  ) {}

  ngOnInit(): void {
    this.clonedProcessConfig = cloneDeep(this.processConfig);

    for (const node of this.processConfig.nodeList.nodeConfigDtos) {
      for (const app of node.nodeConfiguration?.applications) {
        for (const param of app.start.parameters) {
          const appDesc = this.processConfig.nodeList.applications.find((a) => a.key.name === app.application.name).descriptor;
          const paramDesc = appDesc.startCommand.parameters.find((p) => p.uid === param.uid);

          if (!paramDesc) {
            continue; // custom parameter
          }

          if (paramDesc.type === ParameterType.SERVER_PORT || paramDesc.type === ParameterType.CLIENT_PORT || paramDesc.type === ParameterType.URL) {
            if (paramDesc.type === ParameterType.URL) {
              try {
                if (!new URLish(param.value).port) {
                  continue; // no port set
                }
              } catch (err) {
                continue; // ignore this one, not a valid URI!
              }
            }
            if (!this.portsPerApp[app.uid]) {
              this.portsPerApp[app.uid] = [];
            }
            this.appConfigs[app.uid] = app;
            this.portsPerApp[app.uid].push({
              paramCfg: param,
              paramDesc: paramDesc,
              appCfg: app,
              appDesc: appDesc,
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

  onChange(param: ServerPortParameter, value: string, emit: boolean = true) {
    if (param.paramDesc.type === ParameterType.URL) {
      const url = new URLish(param.paramCfg.value);
      if (Number(value) > 65535) {
        value = '65535';
      }
      url.port = value;
      param.paramCfg.value = url.toString();
    } else {
      param.paramCfg.value = value;
    }
    param.paramCfg.preRendered = this.appSvc.preRenderParameter(param.paramDesc, param.paramCfg.value);

    const allApps = this.appSvc.getAllApps(this.processConfig);
    this.appSvc.updateGlobalParameters(param.appDesc, param.appCfg, allApps);

    if (emit) {
      this.validationStateChanged.emit(this.portsForm.valid);
    }
  }

  getPortValue(param: ServerPortParameter) {
    if (param.paramDesc.type === ParameterType.URL) {
      return new URLish(param.paramCfg.value).port;
    }

    return param.paramCfg.value;
  }

  hasPorts(app: ApplicationConfiguration, node: InstanceNodeConfigurationDto): boolean {
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
          selected: true,
          appCfg: spp.appCfg,
          appDesc: spp.appDesc,
        });
      }
    }

    this.dialog
      .open(InstanceShiftPortsComponent, {
        width: '800px',
        data: shiftable,
      })
      .afterClosed()
      .subscribe((r) => {
        if (r) {
          const globalsShifted: ShiftableParameter[] = [];
          for (const shift of shiftable) {
            if (shift.selected) {
              if (shift.desc.global) {
                if (globalsShifted.find((i) => i.desc.uid === shift.desc.uid)) {
                  continue;
                }
                globalsShifted.push(shift);
              }
              const p = { paramCfg: shift.cfg, paramDesc: shift.desc, appCfg: shift.appCfg, appDesc: shift.appDesc };
              this.onChange(p, (Number(this.getPortValue(p)) + Number(r)).toString(), false);
              console.log(shift);
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
      csv +=
        '\n' +
        this.portsPerApp[app]
          .filter((spp) => spp.paramDesc.type === ParameterType.SERVER_PORT)
          .map((spp) => {
            return [this.appConfigs[app].name, spp.paramDesc.name, spp.paramDesc.longDescription, this.getPortValue(spp)];
          })
          .map((r) => r.map((e) => `"${e}"`).join(','))
          .join('\n');
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

  getAppOsName(app: ApplicationConfiguration) {
    return getAppOs(app.application);
  }
}
