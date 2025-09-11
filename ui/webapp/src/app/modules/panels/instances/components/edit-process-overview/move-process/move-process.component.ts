import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Subscription, combineLatest } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { ApplicationType, InstanceNodeConfigurationDto, NodeType, OperatingSystem } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { updateAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProcessEditService } from '../../../services/process-edit.service';
import { BdDialogComponent } from '../../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { RouterLink } from '@angular/router';
import { BdDataTableComponent } from '../../../../../core/components/bd-data-table/bd-data-table.component';

interface NodeRow {
  name: string;
  type: ApplicationType;
  os: OperatingSystem;
  current: boolean;
  node: InstanceNodeConfigurationDto;
}

const colNodeName: BdDataColumn<NodeRow, string> = {
  id: 'name',
  name: 'Node',
  data: (r) => `${r.name}${r.current ? ' - Current' : ''}`,
  classes: (r) => (r.current ? ['bd-disabled-text'] : []),
};

@Component({
    selector: 'app-move-process',
    templateUrl: './move-process.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, RouterLink, BdDataTableComponent]
})
export class MoveProcessComponent implements OnInit, OnDestroy {
  private readonly areas = inject(NavAreasService);
  public readonly instanceEdit = inject(InstanceEditService);
  public readonly edit = inject(ProcessEditService);

  protected records: NodeRow[] = [];
  protected readonly columns: BdDataColumn<NodeRow, unknown>[] = [colNodeName];

  private currentNode: InstanceNodeConfigurationDto;
  private subscription: Subscription;

  ngOnInit() {
    this.subscription = combineLatest([
      this.instanceEdit.state$,
      this.edit.application$,
      this.edit.process$,
      this.instanceEdit.nodes$,
    ]).subscribe(([state, app, process, nodes]) => {
      if (!state || !app || !process || !nodes) {
        this.records = [];
        this.currentNode = null;
        return;
      }

      this.currentNode = state.config.nodeDtos.find(
        (n) => !!n.nodeConfiguration.applications.find((a) => a.id === process.id),
      );

      const result: NodeRow[] = [];
      for (const node of state.config.nodeDtos) {
        const appType = node.nodeConfiguration.nodeType === NodeType.CLIENT ? ApplicationType.CLIENT : ApplicationType.SERVER;
        if (app.descriptor.type !== appType) {
          continue;
        }

        let nodeOs = null;
        if (appType === ApplicationType.SERVER) {
          const nodeDetails = nodes[node.nodeName];
          if (!nodeDetails) {
            continue;
          }

          nodeOs = nodeDetails.os;
          if (!app.descriptor.supportedOperatingSystems.includes(nodeOs)) {
            continue;
          }
        }

        const name = this.niceName(node);
        result.push({
          name: name,
          current: node.nodeName === this.currentNode.nodeName,
          node: node,
          os: nodeOs,
          type: appType,
        });
      }

      this.records = result;
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private niceName(node: InstanceNodeConfigurationDto) {
    return node.nodeConfiguration.nodeType === NodeType.CLIENT ? 'Client Applications' : node.nodeName;
  }

  protected onSelectNode(node: NodeRow) {
    if (node.current) {
      // prevent move to self.
      return;
    }

    const cfg = this.edit.process$.value;

    const targetNode = this.instanceEdit.state$.value?.config?.nodeDtos?.find(
      (n) => n.nodeName === node.name,
    )?.nodeConfiguration;
    const targetApps = targetNode?.applications;

    if (!targetNode) {
      return;
    }

    // remove the current process.
    this.edit.removeProcess();

    if (node.type !== ApplicationType.CLIENT) {
      updateAppOs(cfg.application, node.os);
    }

    targetApps.push(cfg);
    this.instanceEdit.getLastControlGroup(targetNode).processOrder.push(cfg.id);
    this.instanceEdit.conceal(`Move ${cfg.name} from ${this.niceName(this.currentNode)} to ${node.name}`);

    // this edit is so severe that none of the panels (edit overview, etc.) will work as data is shifted. close panels completely.
    this.areas.closePanel();
  }
}
