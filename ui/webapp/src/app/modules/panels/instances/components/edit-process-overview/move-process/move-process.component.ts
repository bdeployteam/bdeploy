import { Component, OnDestroy, OnInit } from '@angular/core';
import { combineLatest, Subscription } from 'rxjs';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { BdDataColumn } from 'src/app/models/data';
import { ApplicationType, InstanceNodeConfigurationDto, OperatingSystem } from 'src/app/models/gen.dtos';
import { updateAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProcessEditService } from '../../../services/process-edit.service';

interface NodeRow {
  name: string;
  type: ApplicationType;
  os: OperatingSystem;
  current: boolean;
  node: InstanceNodeConfigurationDto;
}

const colNodeName: BdDataColumn<NodeRow> = {
  id: 'name',
  name: 'Node',
  data: (r) => `${r.name}${r.current ? ' - Current' : ''}`,
  classes: (r) => (r.current ? ['bd-disabled-text'] : []),
};

@Component({
  selector: 'app-move-process',
  templateUrl: './move-process.component.html',
  styleUrls: ['./move-process.component.css'],
})
export class MoveProcessComponent implements OnInit, OnDestroy {
  /* template */ records: NodeRow[] = [];
  /* template */ columns: BdDataColumn<NodeRow>[] = [colNodeName];

  private currentNode: InstanceNodeConfigurationDto;
  private subscription: Subscription;

  constructor(public instanceEdit: InstanceEditService, public edit: ProcessEditService) {
    this.subscription = combineLatest([this.instanceEdit.state$, this.edit.application$, this.edit.process$, this.instanceEdit.nodes$]).subscribe(
      ([state, app, process, nodes]) => {
        if (!state || !app || !process || !nodes) {
          this.records = [];
          this.currentNode = null;
          return;
        }

        this.currentNode = state.nodeDtos.find((n) => !!n.nodeConfiguration.applications.find((a) => a.uid === process.uid));

        const result: NodeRow[] = [];
        for (const node of state.nodeDtos) {
          const nodeType = node.nodeName === CLIENT_NODE_NAME ? ApplicationType.CLIENT : ApplicationType.SERVER;
          if (app.descriptor.type !== nodeType) {
            continue;
          }

          let nodeOs = null;
          if (nodeType === ApplicationType.SERVER) {
            const nodeDetails = nodes[node.nodeName];
            if (!nodeDetails) {
              continue;
            }

            nodeOs = nodeDetails.os;
            if (!app.descriptor.supportedOperatingSystems.includes(nodeOs)) {
              continue;
            }
          }

          const name = this.niceName(node.nodeName);
          result.push({ name: name, current: node.nodeName === this.currentNode.nodeName, node: node, os: nodeOs, type: nodeType });
        }

        this.records = result;
      }
    );
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private niceName(node: string) {
    return node === CLIENT_NODE_NAME ? 'Client Applications' : node;
  }

  /* template */ onSelectNode(node: NodeRow) {
    if (node.current) {
      // prevent move to self.
      return;
    }

    const cfg = this.edit.process$.value;

    const origApps = this.instanceEdit.state$.value?.nodeDtos?.find((n) => n.nodeName === this.currentNode.nodeName)?.nodeConfiguration?.applications;
    const targetApps = this.instanceEdit.state$.value?.nodeDtos?.find((n) => n.nodeName === node.name)?.nodeConfiguration?.applications;

    if (!origApps || !targetApps) {
      return;
    }

    origApps.splice(
      origApps.findIndex((a) => a.uid === cfg.uid),
      1
    );
    if (node.type !== ApplicationType.CLIENT) {
      updateAppOs(cfg.application, node.os);
    }
    targetApps.push(cfg);

    this.instanceEdit.conceal(`Move ${cfg.name} from ${this.niceName(this.currentNode.nodeName)} to ${node.name}`);
  }
}
