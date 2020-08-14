import { AfterViewInit, Component, Inject, OnInit, ViewChild } from '@angular/core';
import { MAT_BOTTOM_SHEET_DATA } from '@angular/material/bottom-sheet';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { forkJoin, Observable } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { ApplicationDto, InstanceNodeConfiguration, InstanceNodeConfigurationDto, ParameterType } from 'src/app/models/gen.dtos';
import { ApplicationService } from '../../services/application.service';
import { InstanceService } from '../../services/instance.service';

interface Row {
  instance: string,
  application: string,
  description: string,
  port: number,
}

interface InstanceNodePortSheetData {
  instanceGroup: string;
  instanceId: string;
  minionName: string;
  node: InstanceNodeConfigurationDto;
}

@Component({
  selector: 'app-instance-node-port-list',
  templateUrl: './instance-node-port-list.component.html',
  styleUrls: ['./instance-node-port-list.component.css']
})
export class InstanceNodePortListComponent implements OnInit, AfterViewInit {

  public INITIAL_SORT_COLUMN = 'port';
  public INITIAL_SORT_DIRECTION = 'asc';

  public displayedColumns: string[] = ['instance', 'application', 'description', 'port', 'state'];
  public dataSource: MatTableDataSource<Row> = new MatTableDataSource<Row>([]);

  @ViewChild(MatSort)
  sort: MatSort;

  loading = true;
  states: { [key: number]: boolean; };
  rows = [];


  constructor(
    @Inject(MAT_BOTTOM_SHEET_DATA) public data: InstanceNodePortSheetData,
    private instanceService: InstanceService,
    private applicationService: ApplicationService
  ) { }

  ngOnInit() {
    const observables : Observable<Row[]>[] = [];
    // ports of main instance
    if(this.data.node.nodeConfiguration) {
      observables.push(
        this.applicationService.listApplications(this.data.instanceGroup, this.data.node.nodeConfiguration.product, false)
          .pipe(map(apps => this.collectServerPorts(this.data.node.nodeConfiguration, apps)))
      );
    }
    // ports of foreign instances
    this.data.node.foreignNodeConfigurations.forEach(node => {
      observables.push(
        this.applicationService.listApplications(this.data.instanceGroup, node.product, false)
          .pipe(map(apps => this.collectServerPorts(node, apps)))
      );
    });
    if(observables.length == 0) {
      this.loading = false;
    }

    forkJoin(observables).subscribe(results => {
      results.forEach(r => this.rows.push(...r));

      const ports = this.rows.map(row => row.port);
      this.instanceService.getOpenPorts(this.data.instanceGroup, this.data.instanceId, this.data.minionName, ports)
        .pipe(finalize(() => {this.loading = false;}))
        .subscribe(r => {
          this.states = r;
          this.dataSource.data = this.rows; // triggers update
        });
      });

  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.dataSource.data = this.rows;
  }

  private collectServerPorts(instanceNodeConfiguration: InstanceNodeConfiguration, applications: ApplicationDto[]): Row[] {
    const rows = [];
    for (const app of instanceNodeConfiguration.applications) {
      const appDesc = applications.find(a => a.key.name === app.application.name).descriptor;
      for (const paramCfg of app.start.parameters) {
        const paramDesc = appDesc.startCommand.parameters.find(p => p.uid === paramCfg.uid);
        if (paramDesc.type === ParameterType.SERVER_PORT) {
          const row = {
            instance: instanceNodeConfiguration.name,
            application: app.name,
            description: paramDesc.name,
            port: paramCfg.value,
          };
          rows.push(row);
        }
      }
    }
    return rows;
  }
}
