import { Injectable } from '@angular/core';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { ApplicationConfiguration, ParameterConfiguration, ParameterDescriptor, ParameterType } from 'src/app/models/gen.dtos';
import { URLish } from 'src/app/modules/core/utils/url.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';

export interface PortParmGroup {
  apps: ApplicationConfiguration[];
  params: ParameterConfiguration[];
  desc: ParameterDescriptor;
  port: string;
}

@Injectable({
  providedIn: 'root',
})
export class PortsEditService {
  public ports$ = new BehaviorSubject<PortParmGroup[]>(null);

  constructor(public edit: InstanceEditService) {
    combineLatest([this.edit.state$, this.edit.stateApplications$]).subscribe(([s, a]) => {
      if (!s || !a) {
        this.ports$.next(null);
        return;
      }

      const portParams: PortParmGroup[] = [];

      for (const node of s.config.nodeDtos) {
        for (const app of node.nodeConfiguration.applications) {
          for (const param of app.start.parameters) {
            const appDesc = this.edit.getApplicationDescriptor(app.application.name);
            const paramDesc = appDesc?.startCommand?.parameters?.find((p) => p.uid === param.uid);

            if (!paramDesc || !this.isPortParam(paramDesc)) {
              continue;
            }

            const ppg = portParams.find((p) => p.desc.uid === paramDesc.uid && p.desc.global && paramDesc.global && p.params[0].value === param.value);
            if (!ppg) {
              portParams.push({ apps: [app], params: [param], desc: paramDesc, port: this.getPortValue(param, paramDesc) });
            } else {
              ppg.params.push(param);
              ppg.apps.push(app);
            }
          }
        }
      }

      this.ports$.next(portParams);
    });
  }

  public shiftPorts(ports: PortParmGroup[], amount: number): string[] {
    if (!ports?.length) {
      return;
    }

    const errors: string[] = [];

    for (const group of ports) {
      const num = parseInt(group.port, 10) + amount;

      if (num < 0 || num > 65535) {
        errors.push(`${group.desc.name}: ${num} out of range.`);
        continue;
      }

      this.setPortValue(group, `${num}`);
    }

    if (!!errors.length) {
      this.edit.discard(); // discard whatever we did...
    } else {
      this.edit.conceal(`Shift ports by ${amount}`);
    }

    return errors;
  }

  private getPortValue(param: ParameterConfiguration, desc: ParameterDescriptor) {
    if (desc.type === ParameterType.URL) {
      // the instance-edit-ports component will give us only parameters where this is valid!
      return new URLish(param.value).port;
    }
    return param.value;
  }

  private setPortValue(group: PortParmGroup, value: string) {
    for (const param of group.params) {
      if (group.desc.type === ParameterType.URL) {
        const u = new URLish(param.value);
        u.port = value;
        param.value = u.toString();
      } else {
        param.value = value;
      }
    }
    group.port = value;
  }

  private isPortParam(desc: ParameterDescriptor) {
    switch (desc.type) {
      case 'CLIENT_PORT':
      case 'SERVER_PORT':
      case 'URL':
        return true;
    }
    return false;
  }
}
