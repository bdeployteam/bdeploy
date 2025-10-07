import { Component, inject, Input, OnChanges, OnInit, signal } from '@angular/core';
import { PortStateDto, VariableType } from 'src/app/models/gen.dtos';
import { PortsService } from 'src/app/modules/primary/instances/services/ports.service';
import { PinnedParameter } from '../process-status.component';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { NgClass } from '@angular/common';
import { BdDataColumn } from '../../../../../../models/data';
import { CellComponent } from '../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

interface PortStateDisplay {
  tooltip: string | null;
  icon: string;
  isBad: boolean;
}

@Component({
  selector: 'app-pinned-parameter-value',
  templateUrl: './pinned-parameter-value.component.html',
  styleUrls: ['./pinned-parameter-value.component.css'],
  imports: [MatCheckbox, MatTooltip, MatIcon, NgClass]
})
export class PinnedParameterValueComponent implements OnInit, OnChanges, CellComponent<PinnedParameter, string> {
  private readonly ports = inject(PortsService);

  @Input() record: PinnedParameter;
  @Input() column: BdDataColumn<PinnedParameter, string>;

  protected booleanValue: boolean;

  private static UNKNOWN_PORT_STATE: PortStateDisplay = {
    tooltip: null,
    icon: 'help',
    isBad: true
  };

  protected portStateDisplay = signal(PinnedParameterValueComponent.UNKNOWN_PORT_STATE);

  ngOnInit(): void {
    if (this.record.type == VariableType.SERVER_PORT) {
      this.ports.activePortStates$.subscribe((instancePortStates) => {
        this.portStateDisplay.set(this.getDisplayInfoFrom(
          instancePortStates?.[this.record.appId]?.portStates?.find((p) => p.paramId === this.record.paramId)?.states,
          this.record.serverNode
        ));
      });
    }

    this.ngOnChanges();
  }

  ngOnChanges(): void {
    if (this.record.type === VariableType.BOOLEAN) {
      this.booleanValue = this.record.value === 'true';
    }
  }

  private getDisplayInfoFrom(states: PortStateDto[], serverNode?: string): PortStateDisplay {
    if (serverNode) {
      const portState = states?.find(portState => portState.serverNode == serverNode);
      if (portState?.isUsed === true) {
        return {
          isBad: false,
          tooltip: 'Port is in open state.',
          icon: 'link'
        };
      } else if (portState?.isUsed === false) {
        return {
          isBad: true,
          tooltip: 'Port is not in open state.',
          icon: 'link_off'
        };
      }
    } else if (states) {
      let nrOfPortsChecked = 0;
      let nrOfPortsOpen = 0;
      states.forEach(nodeState => {
        nrOfPortsChecked++;
        nrOfPortsOpen += nodeState.isUsed ? 1 : 0;
      });

      if (nrOfPortsChecked == nrOfPortsOpen) {
        return {
          isBad: false,
          tooltip: `All ${nrOfPortsChecked} nodes have this port in open state.`,
          icon: 'link'
        };
      } else if (nrOfPortsOpen == 0) {
        return {
          isBad: true,
          tooltip: `None of the ${nrOfPortsChecked} nodes checked have this port in open state.`,
          icon: 'link_off'
        };
      } else {
        return {
          isBad: true,
          tooltip: `${nrOfPortsOpen} of the ${nrOfPortsChecked} nodes checked have this port in open state.`,
          icon: 'link_off'
        };
      }
    }

    return PinnedParameterValueComponent.UNKNOWN_PORT_STATE;
  }

}
