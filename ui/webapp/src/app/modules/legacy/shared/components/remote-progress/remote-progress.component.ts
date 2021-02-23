import { NestedTreeControl } from '@angular/cdk/tree';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, TemplateRef } from '@angular/core';
import { MatBottomSheet } from '@angular/material/bottom-sheet';
import { MatTreeNestedDataSource } from '@angular/material/tree';
import { Subscription } from 'rxjs';
import { ObjectChangeDetails, ObjectChangeType } from 'src/app/models/gen.dtos';
import { ObjectChangesService } from 'src/app/modules/core/services/object-changes.service';
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
  private _scope: string[];
  private subscription: Subscription;

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
    private systemService: SystemService,
    private changes: ObjectChangesService
  ) {}

  ngOnInit() {}

  ngOnDestroy() {
    this.stopEventListener();
  }

  private updateRemoteEvents(e: string) {
    const list = this.eventsService.parseEvent(e, this._scope);
    if (list && list.length === 0) {
      this.remoteProgressElements = null;
      this.events.emit([]); // explicit "reset".
    } else {
      this.remoteProgressElements = list;
      this.events.emit(list);
    }
    this.treeDataSource.data = this.remoteProgressElements;
  }

  private startEventListener() {
    this.subscription = this.changes.subscribe(
      ObjectChangeType.ACTIVITIES,
      { scope: this._scope },
      (e) => this.updateRemoteEvents(e.details[ObjectChangeDetails.ACTIVITIES]),
      (_) => {
        this.systemService.backendUnreachable();
        this.remoteProgressElements = null;
      }
    );
  }

  private stopEventListener() {
    if (!!this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  showProgressSheet(template: TemplateRef<any>) {
    this.bottomSheet.open(template, { panelClass: 'progress-panel' });
  }
}
