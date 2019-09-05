import { NestedTreeControl } from '@angular/cdk/tree';
import { Component, Input, OnDestroy, OnInit, TemplateRef } from '@angular/core';
import { MatBottomSheet, MatTreeNestedDataSource } from '@angular/material';
import { EventSourcePolyfill } from 'ng-event-source';
import { LoggingService } from '../services/logging.service';
import { ActivitySnapshotTreeNode, RemoteEventsService } from '../services/remote-events.service';
import { SystemService } from '../services/system.service';

@Component({
  selector: 'app-remote-progress',
  templateUrl: './remote-progress.component.html',
  styleUrls: ['./remote-progress.component.css'],
})
export class RemoteProgressComponent implements OnInit, OnDestroy {
  private log = this.loggingService.getLogger('RemoteProgressComponent');

  public remoteProgressElements: ActivitySnapshotTreeNode[];
  private eventSource: EventSourcePolyfill;
  private _scope: string[];

  @Input() set scope(v: string[]) {
    this._scope = v;
    this.stopEventListener();
    this.startEventListener();
  }

  treeControl = new NestedTreeControl<ActivitySnapshotTreeNode>(n => n.children);
  treeDataSource = new MatTreeNestedDataSource<ActivitySnapshotTreeNode>();
  hasChild = (_: number, node: ActivitySnapshotTreeNode) => !!node.children && node.children.length > 0;

  constructor(private events: RemoteEventsService, private loggingService: LoggingService, private bottomSheet: MatBottomSheet, private systemService: SystemService) {}

  ngOnInit() {}

  ngOnDestroy() {
    this.stopEventListener();
  }

  private updateRemoteEvents(message: MessageEvent, scope: string[]) {
    const list = this.events.parseEvent(message, scope);
    if (list && list.length === 0) {
      this.remoteProgressElements = null;
    } else {
      this.remoteProgressElements = list;
    }
    this.treeDataSource.data = this.remoteProgressElements;
  }

  private startEventListener() {
    this.eventSource = this.events.getGlobalEventSource();
    this.eventSource.onerror = err => {
      this.systemService.backendUnreachable();
      this.remoteProgressElements = null;
    };
    this.eventSource.addEventListener('activities', e => this.updateRemoteEvents(e as MessageEvent, this._scope));
  }

  private stopEventListener() {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }

  showProgressSheet(template: TemplateRef<any>) {
    this.bottomSheet.open(template, { panelClass: 'progress-panel' });
  }
}
