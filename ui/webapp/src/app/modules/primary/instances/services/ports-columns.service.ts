import { Injectable } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { PortStateColumnComponent } from '../components/port-state-column/port-state-column.component';
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
  component: PortStateColumnComponent,
  width: '40px',
};

@Injectable({
  providedIn: 'root',
})
export class PortsColumnsService {
  private portRatingCol: BdDataColumn<NodeApplicationPort> = {
    id: 'rating',
    name: 'Rating',
    data: (r) => this.getRating(r),
    component: PortStateColumnComponent,
    width: '40px',
  };

  public defaultPortsColumns: BdDataColumn<NodeApplicationPort>[] = [portNameCol, portNumCol, portStateCol, this.portRatingCol];
  public defaultPortsColumnsWithApp: BdDataColumn<NodeApplicationPort>[] = [portAppCol, ...this.defaultPortsColumns];

  constructor(private processes: ProcessesService) {}

  private getRating(r: NodeApplicationPort) {
    const currentStates = this.processes.processStates$.value;
    const ps = ProcessesService.get(currentStates, r.appUid);

    if (!ps) {
      return false; // never OK, don't know process state
    }

    if (ProcessesService.isRunning(ps.processState) === r.state) {
      return true; // running and open, or not running and not open - yay!
    }

    return false; // either not running, or not open.
  }
}
