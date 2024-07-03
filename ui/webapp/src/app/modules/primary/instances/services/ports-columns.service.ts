import { Injectable, inject } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { RatingStatusColumnComponent } from '../components/rating-status-column/rating-status-column.component';
import { StateStatusColumnComponent } from '../components/state-status-column/state-status-column.component';
import { NodeApplicationPort } from './ports.service';
import { ProcessesService } from './processes.service';

const portAppCol: BdDataColumn<NodeApplicationPort> = {
  id: 'app',
  name: 'Application',
  data: (r) => r.appName,
};

const portNameCol: BdDataColumn<NodeApplicationPort> = {
  id: 'name',
  name: 'Parameter',
  data: (r) => r.paramName,
};

const portNumCol: BdDataColumn<NodeApplicationPort> = {
  id: 'port',
  name: 'Port',
  data: (r) => r.port,
};

const portStateCol: BdDataColumn<NodeApplicationPort> = {
  id: 'state',
  name: 'State',
  data: (r) => r.state,
  component: StateStatusColumnComponent,
  width: '40px',
};

@Injectable({
  providedIn: 'root',
})
export class PortsColumnsService {
  private readonly processes = inject(ProcessesService);

  private readonly portRatingCol: BdDataColumn<NodeApplicationPort> = {
    id: 'rating',
    name: 'Rating',
    data: (r) => this.getRating(r),
    component: RatingStatusColumnComponent,
    width: '40px',
  };

  public readonly defaultPortsColumns: BdDataColumn<NodeApplicationPort>[] = [
    portNameCol,
    portNumCol,
    portStateCol,
    this.portRatingCol,
  ];
  public readonly defaultPortsColumnsWithApp: BdDataColumn<NodeApplicationPort>[] = [
    portAppCol,
    ...this.defaultPortsColumns,
  ];

  private getRating(r: NodeApplicationPort) {
    const currentStates = this.processes.processStates$.value;
    const ps = ProcessesService.get(currentStates, r.appId);
    const isRunning = ProcessesService.isRunning(ps.processState);

    if (!ps) {
      return {
        status: false,
        message: 'Process state is unknown.',
      };
    }

    if (isRunning && r.state === true) {
      return {
        status: true,
        message: `Port is in open state and process is running.`,
      };
    }

    if (!isRunning && r.state === false) {
      return {
        status: true,
        message: `Port is not in open state and process is not running.`,
      };
    }

    if (!isRunning && r.state === true) {
      return {
        status: false,
        message: `Port is in open state and process is not running.`,
      };
    }

    if (isRunning && r.state === false) {
      return {
        status: false,
        message: `Port is not in open state and process is running.`,
      };
    }

    return {
      status: null,
      message: null,
    };
  }
}
