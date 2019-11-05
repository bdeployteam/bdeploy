import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { format } from 'date-fns';
import { finalize } from 'rxjs/operators';
import { MessageBoxMode } from '../messagebox/messagebox.component';
import { AttachIdentDto, InstanceConfiguration, NodeStatus } from '../models/gen.dtos';
import { ConfigService } from '../services/config.service';
import { MessageboxService } from '../services/messagebox.service';

interface NodeRecord {
    key: string;
    status: NodeStatus;
}

@Component({
  selector: 'app-managed-server-detail',
  templateUrl: './managed-server-detail.component.html',
  styleUrls: ['./managed-server-detail.component.css']
})
export class ManagedServerDetailComponent implements OnInit {


  @Input()
  public server: AttachIdentDto;

  @Input()
  public instanceGroupName: string;

  @Output()
  public delete = new EventEmitter<AttachIdentDto>();

  loading = true;
  instances: InstanceConfiguration[];
  columnsToDisplay = ['minion', 'os', 'version', 'startup', 'master'];

  public dataSource: MatTableDataSource<NodeRecord>;

  constructor(private config: ConfigService, private mbService: MessageboxService) { }

  ngOnInit() {
    this.config.getInstancesForManagedServer(this.instanceGroupName, this.server.name).subscribe(r => {
      this.instances = r;
    });

    this.config.minionsOfManagedServer(this.instanceGroupName, this.server.name).pipe(finalize(() => this.loading = false)).subscribe(r => {
      const arr: NodeRecord[] = [];
      for (const key of Object.keys(r)) {
        arr.push({key: key, status: r[key]});
      }

      this.dataSource = new MatTableDataSource<NodeRecord>(arr);
    });
  }

  async doDelete() {
    let doIt = false;
    if (this.instances.length > 0) {
      doIt = await this.mbService.openAsync({title: 'Delete attached Managed Server', message: `Are you sure you want to delete the selected managed server from the central server? This will delete <b>${this.instances.length}</b> instances from the central server as well (but not from the managed server).`, mode: MessageBoxMode.CONFIRM_WARNING});
    } else {
      doIt = await this.mbService.openAsync({title: 'Delete attached Managed Server', message: 'Are you sure you want to delete the selected managed server from the central server?', mode: MessageBoxMode.CONFIRM});
    }
    if (doIt) {
      await this.config.deleteManagedServer(this.instanceGroupName, this.server.name).toPromise();
      this.delete.emit(this.server);
    }
  }

  getDate(x: number) {
    return format(new Date(x), 'dd.MM.yyyy HH:mm');
  }
}
