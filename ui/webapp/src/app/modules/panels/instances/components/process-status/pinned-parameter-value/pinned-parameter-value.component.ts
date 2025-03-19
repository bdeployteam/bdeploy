import { Component, Input, OnChanges, OnInit, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { VariableType } from 'src/app/models/gen.dtos';
import { NodeApplicationPort, PortsService } from 'src/app/modules/primary/instances/services/ports.service';
import { PinnedParameter } from '../process-status.component';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { AsyncPipe } from '@angular/common';
import { BdDataColumn } from '../../../../../../models/data';
import { CellComponent } from '../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-pinned-parameter-value',
    templateUrl: './pinned-parameter-value.component.html',
    styleUrls: ['./pinned-parameter-value.component.css'],
    imports: [MatCheckbox, MatTooltip, MatIcon, AsyncPipe]
})
export class PinnedParameterValueComponent implements OnInit, OnChanges, CellComponent<PinnedParameter, string> {
  private readonly ports = inject(PortsService);

  @Input() record: PinnedParameter;
  @Input() column: BdDataColumn<PinnedParameter, string>;

  protected portState$ = new BehaviorSubject<NodeApplicationPort>(null);
  protected booleanValue: boolean;

  ngOnInit(): void {
    this.ports.activePortStates$.subscribe((a) => {
      const port = a?.find((p) => p.appId === this.record.appId && p.paramId === this.record.paramId);
      this.portState$.next(port);
    });

    this.ngOnChanges();
  }

  ngOnChanges(): void {
    if (this.record.type === VariableType.BOOLEAN) {
      this.booleanValue = this.record.value === 'true';
    }
  }
}
