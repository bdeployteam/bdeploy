import { Component, OnInit, ViewChild } from '@angular/core';
import { format } from 'date-fns';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { ManagedMasterDto, OperatingSystem } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ServerDetailsService } from '../../services/server-details.service';
import { ServerOsComponent } from './server-os/server-os.component';

export interface MinionRow {
  name: string;
  os: OperatingSystem;
  master: boolean;
  version: string;
}

const detailNameCol: BdDataColumn<MinionRow> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
};

const detailMasterCol: BdDataColumn<MinionRow> = {
  id: 'master',
  name: 'Master',
  data: (r) => (r.master ? 'Yes' : ''),
  width: '60px',
};

const detailVersionCol: BdDataColumn<MinionRow> = {
  id: 'version',
  name: 'Vers.',
  data: (r) => r.version,
  width: '60px',
};

const detailOsCol: BdDataColumn<MinionRow> = {
  id: 'os',
  name: 'OS',
  data: (r) => r.os,
  component: ServerOsComponent,
  width: '30px',
};

@Component({
  selector: 'app-server-details',
  templateUrl: './server-details.component.html',
  styleUrls: ['./server-details.component.css'],
  providers: [ServerDetailsService],
})
export class ServerDetailsComponent implements OnInit {
  private deleting$ = new BehaviorSubject<boolean>(false);

  /* template */ loading$ = combineLatest([this.deleting$, this.servers.loading$, this.serverDetails.loading$]).pipe(map(([a, b, c]) => a || b || c));
  /* template */ columns = [detailNameCol, detailVersionCol, detailMasterCol, detailOsCol];
  /* template */ synchronizing$ = new BehaviorSubject<boolean>(false);

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(public servers: ServersService, public serverDetails: ServerDetailsService, public auth: AuthenticationService, public areas: NavAreasService) {}

  ngOnInit(): void {}

  /* template */ formatDate(x: number) {
    return format(new Date(x), 'dd.MM.yyyy HH:mm');
  }

  /* template */ getMinionRecords(server: ManagedMasterDto): MinionRow[] {
    return Object.keys(server.minions.minions).map((k) => {
      const dto = server.minions.minions[k];
      return {
        name: k,
        os: dto.os,
        master: dto.master,
        version: convert2String(dto.version),
      };
    });
  }

  /* template */ doDelete(server: ManagedMasterDto) {
    this.dialog
      .confirm(
        `Delete ${server.hostName}`,
        `Are you sure you want to delete ${server.hostName}?` +
          (!this.serverDetails.instances$.value?.length
            ? ''
            : ` This will delete <strong>${this.serverDetails.instances$.value.length} instance(s)</strong> from the central server ` +
              '- they will <strong>not</strong> be deleted from the managed server.')
      )
      .subscribe((del) => {
        if (del) {
          this.deleting$.next(true);
          this.serverDetails
            .delete(server)
            .pipe(finalize(() => this.deleting$.next(false)))
            .subscribe((_) => this.areas.closePanel());
        }
      });
  }

  /* template */ doSynchronize(server: ManagedMasterDto) {
    this.synchronizing$.next(true);
    this.servers
      .synchronize(server)
      .pipe(finalize(() => this.synchronizing$.next(false)))
      .subscribe();
  }
}
