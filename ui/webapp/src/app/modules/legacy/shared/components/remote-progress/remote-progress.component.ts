import { NestedTreeControl } from '@angular/cdk/tree';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, TemplateRef } from '@angular/core';
import { MatBottomSheet } from '@angular/material/bottom-sheet';
import { MatTreeNestedDataSource } from '@angular/material/tree';
import ReconnectingWebSocket from 'reconnecting-websocket';
import { LoggingService } from '../../../../core/services/logging.service';
import { SystemService } from '../../../../core/services/system.service';
import { ActivitySnapshotTreeNode, RemoteEventsService } from '../../services/remote-events.service';

@Component({
  selector: 'app-remote-progress',
  templateUrl: './remote-progress.component.html',
  styleUrls: ['./remote-progress.component.css'],
})
export class RemoteProgressComponent implements OnInit, OnDestroy {
  private log = this.loggingService.getLogger('RemoteProgressComponent');

  public remoteProgressElements: ActivitySnapshotTreeNode[];
  private ws: ReconnectingWebSocket;
  private _scope: string[];

  @Input() set scope(v: string[]) {
    this._scope = v;
    this.stopEventListener();
    this.startEventListener();
  }

  @Output()
  public events = new EventEmitter<ActivitySnapshotTreeNode[]>();

  treeControl = new NestedTreeControl<ActivitySnapshotTreeNode>((n) => n.children);
  treeDataSource = new MatTreeNestedDataSource<ActivitySnapshotTreeNode>();
  hasChild = (_: number, node: ActivitySnapshotTreeNode) => !!node.children && node.children.length > 0;

  constructor(
    private eventsService: RemoteEventsService,
    private loggingService: LoggingService,
    private bottomSheet: MatBottomSheet,
    private systemService: SystemService
  ) {}

  ngOnInit() {}

  ngOnDestroy() {
    this.stopEventListener();
  }

  private updateRemoteEvents(message: MessageEvent, scope: string[]) {
    const blob = message.data as Blob;
    const r = new FileReader();
    r.onload = () => {
      const list = this.eventsService.parseEvent(r.result, scope);
      if (list && list.length === 0) {
        this.remoteProgressElements = null;
        this.events.emit([]); // explicit "reset".
      } else {
        this.remoteProgressElements = list;
        this.events.emit(list);
      }
      this.treeDataSource.data = this.remoteProgressElements;
    };
    r.readAsText(blob);
  }

  private startEventListener() {
    this.ws = this.eventsService.createActivitiesWebSocket(this._scope);
    this.ws.addEventListener('error', () => {
      this.systemService.backendUnreachable();
      this.remoteProgressElements = null;
    });
    this.ws.addEventListener('message', (e) => {
      this.updateRemoteEvents(e, this._scope);
    });
  }

  private stopEventListener() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  showProgressSheet(template: TemplateRef<any>) {
    this.bottomSheet.open(template, { panelClass: 'progress-panel' });
  }
}
