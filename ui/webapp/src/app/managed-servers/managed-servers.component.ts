import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { AttachIdentDto } from '../models/gen.dtos';
import { ConfigService } from '../services/config.service';

@Component({
  selector: 'app-managed-servers',
  templateUrl: './managed-servers.component.html',
  styleUrls: ['./managed-servers.component.css']
})
export class ManagedServersComponent implements OnInit {

  instanceGroupName: string = this.route.snapshot.paramMap.get('group');
  managedServers: AttachIdentDto[];
  loading = true;

  connected = new Map<AttachIdentDto, boolean>();

  constructor(private route: ActivatedRoute, private config: ConfigService, public location: Location) { }

  ngOnInit() {
    this.load();
  }

  load() {
    this.config.getManagedServers(this.instanceGroupName).pipe(finalize(() => this.loading = false)).subscribe(r => {
      r.forEach(a => {if (!this.connected.has(a)) {this.connected.set(a, false); }});
      this.managedServers = r.sort((a, b) => a.name.localeCompare(b.name));
    });
  }

  isConnected(server: AttachIdentDto) {
    return this.connected.get(server);
  }

  setConnected(server: AttachIdentDto, con: boolean) {
    this.connected.set(server, con);
  }

}
