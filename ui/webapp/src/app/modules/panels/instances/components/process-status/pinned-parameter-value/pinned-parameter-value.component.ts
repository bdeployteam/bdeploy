import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ParameterType } from 'src/app/models/gen.dtos';
import {
  NodeApplicationPort,
  PortsService,
} from 'src/app/modules/primary/instances/services/ports.service';
import { PinnedParameter } from '../process-status.component';

@Component({
  selector: 'app-pinned-parameter-value',
  templateUrl: './pinned-parameter-value.component.html',
  styleUrls: ['./pinned-parameter-value.component.css'],
})
export class PinnedParameterValueComponent implements OnInit, OnChanges {
  @Input() record: PinnedParameter;

  /* template */ portState$ = new BehaviorSubject<NodeApplicationPort>(null);
  /* template */ booleanValue: boolean;

  constructor(private ports: PortsService) {}

  ngOnInit(): void {
    this.ports.activePortStates$.subscribe((a) => {
      const port = a?.find(
        (p) =>
          p.appUid === this.record.appUid && p.paramUid === this.record.paramUid
      );
      this.portState$.next(port);
    });

    this.ngOnChanges();
  }

  ngOnChanges(): void {
    if (this.record.type === ParameterType.BOOLEAN) {
      this.booleanValue = this.record.value === 'true';
    }
  }
}
