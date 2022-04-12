import { Component } from '@angular/core';
import { NodeAttachDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NodesAdminService } from 'src/app/modules/primary/admin/services/nodes-admin.service';
import { NODE_MIME_TYPE } from '../../add-node/add-node.component';

@Component({
  selector: 'app-node-conversion',
  templateUrl: './node-conversion.component.html',
  styleUrls: ['./node-conversion.component.css'],
})
export class NodeConversionComponent {
  /* template */ data: NodeAttachDto;

  constructor(
    private cfg: ConfigService,
    public nodesAdmin: NodesAdminService
  ) {
    this.nodesAdmin.nodes$.subscribe((n) => {
      if (!n) {
        return;
      }

      const master = n.find((x) => x?.status?.config?.master);

      if (master?.status?.config?.remote) {
        this.data = {
          name: this.cfg.config.hostname,
          sourceMode: this.cfg.config.mode,
          remote: master.status.config.remote,
        };
      }
    });
  }

  /* template */ onDragStart($event) {
    $event.dataTransfer.effectAllowed = 'link';
    $event.dataTransfer.setData(NODE_MIME_TYPE, JSON.stringify(this.data));
  }
}
