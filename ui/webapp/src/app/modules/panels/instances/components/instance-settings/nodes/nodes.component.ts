import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { combineLatest, Observable, of, Subscription } from 'rxjs';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceNodeConfigurationDto, MinionDto } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

interface NodeRow {
  name: string;
  node: MinionDto;
  config: InstanceNodeConfigurationDto;
}

const colNodeName: BdDataColumn<NodeRow> = {
  id: 'name',
  name: 'Node',
  data: (r) => r.name,
};

@Component({
  selector: 'app-nodes',
  templateUrl: './nodes.component.html',
  styleUrls: ['./nodes.component.css'],
})
export class NodesComponent implements OnInit, OnDestroy, DirtyableDialog {
  /* template */ records: NodeRow[] = [];
  /* template */ columns: BdDataColumn<NodeRow>[] = [colNodeName];
  /* template */ checked: NodeRow[] = [];

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  private subscription: Subscription;

  constructor(public edit: InstanceEditService, public servers: ServersService, areas: NavAreasService) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnInit(): void {
    this.subscription.add(
      combineLatest([this.edit.nodes$, this.edit.state$]).subscribe(([nodes, state]) => {
        this.records = [];

        if (!nodes || !state) {
          return;
        }

        for (const key of Object.keys(nodes)) {
          const config = state.nodeDtos.find((n) => n.nodeName === key);
          const row = { name: key, node: nodes[key], config: config };
          this.records.push(row);
          if (!!config) {
            this.checked.push(row);
          }
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  isDirty(): boolean {
    return this.edit.hasPendingChanges();
  }

  /* template */ onCheckedChange(rows: NodeRow[]) {
    // need to propagate changes to the state object.
    for (const node of [...this.edit.state$.value.nodeDtos]) {
      if (node.nodeName === CLIENT_NODE_NAME) {
        continue;
      }

      if (!rows.find((r) => r.name === node.nodeName)) {
        // no longer in the list, remove.
        this.edit.state$.value.nodeDtos.splice(this.edit.state$.value.nodeDtos.indexOf(node), 1);
        this.records.find((r) => r.name === node.nodeName).config = null;
      }
    }

    for (const row of rows) {
      if (!this.edit.state$.value.nodeDtos.find((n) => n.nodeName === row.name)) {
        const inst = this.edit.current$.value;
        this.edit.state$.value.nodeDtos.push(this.edit.createEmptyNode(row.name, inst.instanceConfiguration));
      }
    }
  }

  /* template */ checkChangeAllowed: (row: NodeRow, target: boolean) => Observable<boolean> = (row, target) => {
    // checking is always allowed
    if (target) {
      return of(true);
    }

    if (!!row.config?.nodeConfiguration?.applications?.length) {
      return this.dialog.confirm(
        `Remove Node`,
        `Removing the node <strong>${row.name}</strong> will also remove <strong>${row.config.nodeConfiguration.applications.length}</strong> applications.`,
        'delete'
      );
    }

    return of(true);
  };

  /* template */ doApply() {
    this.edit.conceal('Select Instance Nodes');
    this.tb.closePanel();
  }
}
