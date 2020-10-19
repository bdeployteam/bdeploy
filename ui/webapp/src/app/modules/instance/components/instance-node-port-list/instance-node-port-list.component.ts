import {
  AfterViewInit,
  Component,

  Input,
  OnInit,
  ViewChild
} from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { forkJoin, Observable } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import {
  ApplicationDto,
  InstanceNodeConfiguration,
  InstanceNodeConfigurationDto,
  ParameterType,
  ProcessState
} from 'src/app/models/gen.dtos';
import { ApplicationService } from '../../services/application.service';
import { InstanceService } from '../../services/instance.service';
import { ProcessService } from '../../services/process.service';

interface Row {
  appId: string;
  application: string;
  description: string;
  port: string;
}

@Component({
  selector: 'app-instance-node-port-list',
  templateUrl: './instance-node-port-list.component.html',
  styleUrls: ['./instance-node-port-list.component.css'],
})
export class InstanceNodePortListComponent implements OnInit, AfterViewInit {
  public INITIAL_SORT_COLUMN = 'port';
  public INITIAL_SORT_DIRECTION = 'asc';

  public displayedColumns: string[] = [
    'appState',
    'application',
    'description',
    'portState',
    'port',
    'rating',
  ];
  public dataSource: MatTableDataSource<Row> = new MatTableDataSource<Row>([]);

  @ViewChild(MatSort)
  sort: MatSort;

  loading = true;
  states: { [key: number]: boolean };

  @Input() instanceGroup: string;
  @Input() instanceId: string;
  @Input() instanceTag: string;
  @Input() instanceActiveTag: string;
  @Input() minionName: string;
  @Input() node: InstanceNodeConfigurationDto;

  constructor(
    private instanceService: InstanceService,
    private applicationService: ApplicationService,
    private processService: ProcessService
  ) {}

  ngOnInit() {
    this.reload();
  }

  reload() {
    const observables: Observable<Row[]>[] = [];
    // ports of main instance
    if (this.node.nodeConfiguration) {
      observables.push(
        this.applicationService
          .listApplications(
            this.instanceGroup,
            this.node.nodeConfiguration.product,
            false
          )
          .pipe(
            map((apps) =>
              this.collectServerPorts(this.node.nodeConfiguration, apps)
            )
          )
      );
    }
    if (observables.length === 0) {
      this.loading = false;
    }

    const rows = [];
    forkJoin(observables).subscribe((results) => {
      results.forEach((r) => rows.push(...r));

      const ports = rows.map((row) => row.port);
      this.instanceService
        .getOpenPorts(
          this.instanceGroup,
          this.instanceId,
          this.minionName,
          ports
        )
        .pipe(
          finalize(() => {
            this.loading = false;
          })
        )
        .subscribe((r) => {
          this.states = r;
          this.dataSource.data = rows; // triggers update
        });
    });
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.dataSource.data = [];
  }

  private collectServerPorts(
    instanceNodeConfiguration: InstanceNodeConfiguration,
    applications: ApplicationDto[]
  ): Row[] {
    const rows: Row[] = [];
    for (const app of instanceNodeConfiguration.applications) {
      const appDesc = applications.find(
        (a) => a.key.name === app.application.name
      ).descriptor;
      for (const paramCfg of app.start.parameters) {
        const paramDesc = appDesc.startCommand.parameters.find(
          (p) => p.uid === paramCfg.uid
        );
        if (paramDesc && paramDesc.type === ParameterType.SERVER_PORT) {
          const row: Row = {
            appId: app.uid,
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

  isRunning(element: Row) {
    const state = this.processService.getStatusOfApp(element.appId)?.processState;
    return state === ProcessState.RUNNING || state === ProcessState.RUNNING_UNSTABLE;
  }

  isRatingOk(element: Row) {
    if (this.states && this.states[element.port]) {
      return this.isRunning(element);
    } else {
      return !this.isRunning(element);
    }
  }

}
