import { inject, Injectable } from '@angular/core';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { ApplicationConfiguration, LinkedValueConfiguration, VariableType } from 'src/app/models/gen.dtos';
import { createLinkedValue, getPreRenderable } from 'src/app/modules/core/utils/linked-values.utils';
import { URLish } from 'src/app/modules/core/utils/url.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProcessEditService } from './process-edit.service';

export interface PortParam {
  source: string;
  value: LinkedValueConfiguration;
  type: VariableType;
  name: string;
  description: string;
  port: string;
  expression: boolean;
  app: ApplicationConfiguration;
  apply: (lv: LinkedValueConfiguration) => void;
}

@Injectable({
  providedIn: 'root'
})
export class PortsEditService {
  private readonly procEdit = inject(ProcessEditService);
  protected readonly edit = inject(InstanceEditService);

  public ports$ = new BehaviorSubject<PortParam[]>(null);

  constructor() {
    combineLatest([this.edit.state$, this.edit.stateApplications$]).subscribe(([s, a]) => {
      if (!s || !a) {
        this.ports$.next(null);
        return;
      }

      const portParams: PortParam[] = [];

      for (const node of s.config.nodeDtos) {
        for (const app of node.nodeConfiguration.applications) {
          for (const param of app.start.parameters) {
            const appDesc = this.edit.getApplicationDescriptor(app.application.name);
            const paramDesc = appDesc?.startCommand?.parameters?.find((p) => p.id === param.id);

            if (!this.isPort(paramDesc?.type)) {
              continue;
            }

            portParams.push({
              value: param.value,
              source: `${app.name}`,
              type: paramDesc.type,
              name: paramDesc.name,
              description: paramDesc.longDescription,
              expression: this.isPortExpression(param.value, paramDesc.type),
              port: this.getPortValue(param.value, paramDesc.type),
              app: app,
              apply: (lv) => (param.preRendered = this.procEdit.preRenderParameter(paramDesc, lv))
            });
          }
        }
      }

      // Intentionally DON'T include system variables - we're not allowed to edit them from here!
      if (s?.config?.config?.instanceVariables?.length) {
        // gather instance variables of type port.
        for (const val of s.config.config.instanceVariables) {
          if (!this.isPort(val.type)) {
            continue;
          }

          portParams.push({
            value: val.value,
            source: `Instance Variables.`,
            type: val.type,
            name: val.id,
            description: val.description,
            port: this.getPortValue(val.value, val.type),
            expression: this.isPortExpression(val.value, val.type),
            app: null,
            apply: (lv) => {
              val.value = lv;
            }
          });
        }
      }

      this.ports$.next(portParams);
    });
  }

  public shiftPorts(ports: PortParam[], amount: number): string[] {
    if (!ports?.length) {
      return null;
    }

    const errors: string[] = [];

    for (const port of ports) {
      if (port.expression) {
        continue;
      }

      const current = Number.parseInt(port.port, 10);
      const num = current + amount;

      if (num < 0 || num > 65535) {
        errors.push(`${port.name}: ${num} out of range.`);
        continue;
      }

      this.setPortValue(port, `${num}`);
    }

    if (errors.length) {
      this.edit.discard(); // discard whatever we did...
    } else {
      this.edit.conceal(`Shift ports by ${amount}`);
    }

    return errors;
  }

  private getPortValue(value: LinkedValueConfiguration, type: VariableType): string {
    if (type === VariableType.URL) {
      return new URLish(getPreRenderable(value)).port;
    }
    return value.value; // no link supported if not URL.
  }

  // dont replace port.value as its a shared variable with the actual parameter
  private setPortValue(port: PortParam, value: string) {
    if (port.type === VariableType.URL) {
      const u = new URLish(getPreRenderable(port.value));
      u.port = value;
      const linkedValue = createLinkedValue(u.toString());
      port.value.linkExpression = linkedValue.linkExpression;
      port.value.value = linkedValue.value;
    } else {
      port.value.value = value;
    }

    port.apply(port.value);
    port.port = value;
  }

  private isPort(type: VariableType) {
    switch (type) {
      case VariableType.CLIENT_PORT:
      case VariableType.SERVER_PORT:
      case VariableType.URL:
        return true;
    }
    return false;
  }

  private isPortExpression(value: LinkedValueConfiguration, type: VariableType) {
    const num = Number.parseInt(this.getPortValue(value, type), 10);
    return isNaN(num);
  }
}
