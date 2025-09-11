import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { combineLatest, Observable, of, Subscription } from 'rxjs';
import { tap } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceNodeConfigurationDto, NodeType } from 'src/app/models/gen.dtos';
import {
  BdDialogToolbarComponent
} from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';


import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataTableComponent } from '../../../../../core/components/bd-data-table/bd-data-table.component';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

interface NodeRow {
  name: string;
  config: InstanceNodeConfigurationDto;
}

const colNodeName: BdDataColumn<NodeRow, string> = {
  id: 'name',
  name: 'Node',
  data: (r) => r.name,
  isId: true,
};

@Component({
    selector: 'app-nodes',
    templateUrl: './nodes.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdDataTableComponent, BdButtonComponent, AsyncPipe]
})
export class NodesComponent implements OnInit, OnDestroy, DirtyableDialog {
  private readonly areas = inject(NavAreasService);
  protected readonly edit = inject(InstanceEditService);
  protected readonly servers = inject(ServersService);

  protected records: NodeRow[] = [];
  protected readonly columns: BdDataColumn<NodeRow, unknown>[] = [colNodeName];
  protected checked: NodeRow[] = [];
  protected hasPendingChanges: boolean;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      combineLatest([this.edit.nodes$, this.edit.state$]).subscribe(([nodes, state]) => {
        this.records = [];

        if (!nodes || !state) {
          return;
        }

        // nodes which are configured on the server.
        for (const key of Object.keys(nodes)) {
          const config = state.config.nodeDtos.find((n) => n.nodeName === key);
          const row = { name: key, config: config };
          this.records.push(row);
          if (config) {
            this.checked.push(row);
          }
        }

        // nodes which are not (no longer) present.
        for (const node of state.config.nodeDtos) {
          const hasNode = !!nodes[node.nodeName];
          if (!hasNode && node.nodeConfiguration.nodeType !== NodeType.CLIENT) {
            const row = { name: node.nodeName, config: node };
            this.records.push(row);
            this.checked.push(row);
          }
        }
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  isDirty(): boolean {
    return this.edit.hasPendingChanges();
  }

  protected onCheckedChange(rows: NodeRow[]) {
    // need to propagate changes to the state object.
    for (const node of [...this.edit.state$.value.config.nodeDtos]) {
      if (node.nodeConfiguration.nodeType === NodeType.CLIENT) {
        continue;
      }

      if (!rows.find((r) => r.name === node.nodeName)) {
        // no longer in the list, remove.
        this.edit.state$.value?.config.nodeDtos.splice(this.edit.state$.value?.config.nodeDtos.indexOf(node), 1);
        this.records.find((r) => r.name === node.nodeName).config = null;
      }
    }

    for (const row of rows) {
      if (!this.edit.state$.value?.config.nodeDtos.find((n) => n.nodeName === row.name)) {
        const inst = this.edit.current$.value;
        this.edit.state$.value?.config.nodeDtos.push(this.edit.createEmptyNode(row.name, inst.instanceConfiguration, NodeType.SERVER));
      }
    }
    this.hasPendingChanges = this.edit.hasPendingChanges();
  }

  protected checkChangeAllowed: (row: NodeRow, target: boolean) => Observable<boolean> = (row, target) => {
    // checking is always allowed
    if (target) {
      return of(true);
    }

    if (row.config?.nodeConfiguration?.applications?.length) {
      return this.dialog.confirm(
        `Remove Node`,
        `Removing the node <strong>${row.name}</strong> will also remove <strong>${row.config.nodeConfiguration.applications.length}</strong> applications.`,
        'delete',
      );
    }

    return of(true);
  };

  protected onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public doSave(): Observable<unknown> {
    return of(true).pipe(tap(() => this.edit.conceal('Select Instance Nodes')));
  }
}
