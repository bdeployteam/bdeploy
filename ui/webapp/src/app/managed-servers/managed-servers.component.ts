import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { AttachIdentDto } from '../models/gen.dtos';
import { ManagedServersService } from '../services/managed-servers.service';

@Component({
  selector: 'app-managed-servers',
  templateUrl: './managed-servers.component.html',
  styleUrls: ['./managed-servers.component.css']
})
export class ManagedServersComponent implements OnInit {

  instanceGroupName: string = this.route.snapshot.paramMap.get('group');
  managedServers: AttachIdentDto[];
  loading = true;

  connected = new Map<string, boolean>();

  constructor(private route: ActivatedRoute, public location: Location, private managedServersService: ManagedServersService) { }

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading = true;
    this.managedServersService.getManagedServers(this.instanceGroupName).pipe(finalize(() => this.loading = false)).subscribe(r => {
      this.managedServers = r.sort((a, b) => a.name.localeCompare(b.name));
    });
  }

  isConnected(server: AttachIdentDto) {
    return this.connected.get(server.name);
  }

  setConnected(server: AttachIdentDto, con: boolean) {
    this.connected.set(server.name, con);
  }

}
