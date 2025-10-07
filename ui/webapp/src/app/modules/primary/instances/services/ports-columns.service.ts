import { Injectable } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import {
  PortStatus,
  RatingStatusColumnComponent
} from '../components/rating-status-column/rating-status-column.component';
import { StateStatusColumnComponent } from '../components/state-status-column/state-status-column.component';
import { CompositePortStateDto } from '../../../../models/gen.dtos';
import { ProcessesService } from './processes.service';

const portNameCol: BdDataColumn<CompositePortStateDto, string> = {
  id: 'name',
  name: 'Parameter',
  data: (r) => r.paramName
};

const portNumCol: BdDataColumn<CompositePortStateDto, number> = {
  id: 'port',
  name: 'Port',
  data: (r) => r.port
};

const portStateCol: BdDataColumn<CompositePortStateDto, boolean> = {
  id: 'state',
  name: 'State',
  data: (r) => r.states[0].isUsed,
  component: StateStatusColumnComponent,
  width: '40px'
};

/**
 * provides CompositePortState columns, but will overall compile the data
 * to a single value, so that it can be used for single nodes as well
 */
@Injectable({
  providedIn: 'root'
})
export class PortsColumnsService {

  private readonly portRatingCol: BdDataColumn<CompositePortStateDto, PortStatus> = {
    id: 'rating',
    name: 'Rating',
    data: (r) => this.getRating(r),
    component: RatingStatusColumnComponent,
    width: '40px'
  };

  public readonly defaultPortsColumns: BdDataColumn<CompositePortStateDto, unknown>[] = [
    portNameCol,
    portNumCol,
    portStateCol,
    this.portRatingCol
  ];

  private getRating(compositeState: CompositePortStateDto): PortStatus {
    // Doing an assumption that this is running on a single server
    const ps = compositeState.states[0];
    const isRunning = ProcessesService.isRunning(ps.processState);

    if (!ps) {
      return {
        status: false,
        message: 'Process state is unknown.'
      };
    }

    if (isRunning && ps.isUsed === true) {
      return {
        status: true,
        message: `Port is in open state and process is running.`
      };
    }

    if (!isRunning && ps.isUsed === false) {
      return {
        status: true,
        message: `Port is not in open state and process is not running.`
      };
    }

    if (!isRunning && ps.isUsed === true) {
      return {
        status: false,
        message: `Port is in open state and process is not running.`
      };
    }

    if (isRunning && ps.isUsed === false) {
      return {
        status: false,
        message: `Port is not in open state and process is running.`
      };
    }

    return {
      status: null,
      message: null
    };
  }
}
