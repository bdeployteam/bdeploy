import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { BehaviorSubject, isObservable, Observable, of } from 'rxjs';

export type StateType = 'ok' | 'info' | 'warning' | 'unknown';

export interface StateItem {
  name: string | Observable<string>;
  type: StateType | Observable<StateType>;
  tooltip?: string | Observable<string>;
}
@Component({
  selector: 'app-node-state-panel',
  templateUrl: './state-panel.component.html',
  styleUrls: ['./state-panel.component.css'],
})
export class NodeStatePanelComponent implements OnInit {
  @Input() items: StateItem[];
  @Input() narrowWhen$: BehaviorSubject<boolean>;
  @Input() lastRefreshAt$: BehaviorSubject<number>;

  @Output() manualRefresh = new EventEmitter<any>();

  constructor() {}

  ngOnInit(): void {}

  /* template */ makeObservable<T>(value: T | Observable<T>): Observable<T> {
    if (isObservable(value)) {
      return value;
    }
    return of(value);
  }
}
