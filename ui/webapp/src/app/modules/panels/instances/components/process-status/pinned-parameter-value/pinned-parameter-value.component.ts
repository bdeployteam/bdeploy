import { Component, Input, OnChanges, OnInit, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { VariableType } from 'src/app/models/gen.dtos';
import { NodeApplicationPort, PortsService } from 'src/app/modules/primary/instances/services/ports.service';
import { PinnedParameter } from '../process-status.component';

@Component({
  selector: 'app-pinned-parameter-value',
  templateUrl: './pinned-parameter-value.component.html',
  styleUrls: ['./pinned-parameter-value.component.css'],
})
export class PinnedParameterValueComponent implements OnInit, OnChanges {
  private readonly ports = inject(PortsService);

  @Input() record: PinnedParameter;

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
