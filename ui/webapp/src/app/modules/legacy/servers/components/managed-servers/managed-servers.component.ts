import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { RoutingHistoryService } from 'src/app/modules/legacy/core/services/routing-history.service';
import { ManagedMasterDto } from '../../../../../models/gen.dtos';
import { ManagedServersService } from '../../services/managed-servers.service';

@Component({
  selector: 'app-managed-servers',
  templateUrl: './managed-servers.component.html',
  styleUrls: ['./managed-servers.component.css'],
})
export class ManagedServersComponent implements OnInit {
  instanceGroupName: string = this.route.snapshot.paramMap.get('group');
  managedServers: ManagedMasterDto[];
  loading = true;

  constructor(
    private route: ActivatedRoute,
    public location: Location,
    private managedServersService: ManagedServersService,
    public routingHistoryService: RoutingHistoryService
  ) {}

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading = true;
    this.managedServersService
      .getManagedServers(this.instanceGroupName)
      .pipe(finalize(() => (this.loading = false)))
      .subscribe((r) => {
        this.managedServers = r.sort((a, b) => a.hostName.localeCompare(b.hostName));
      });
  }
}
