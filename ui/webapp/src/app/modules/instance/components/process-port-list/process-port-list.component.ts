import { AfterViewInit, Component, Input, OnInit, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { finalize } from 'rxjs/operators';
import { ProcessState } from 'src/app/models/gen.dtos';
import { InstanceService } from '../../services/instance.service';
import { ProcessService } from '../../services/process.service';

interface Row {
  description: string;
  port: string;
  state: boolean;
  rating: boolean;
}

@Component({
  selector: 'app-process-port-list',
  templateUrl: './process-port-list.component.html',
  styleUrls: ['./process-port-list.component.css']
})
export class ProcessPortListComponent implements OnInit, AfterViewInit {

  @Input() instanceGroup: string;
  @Input() instanceId: string;
  @Input() minionName: string;
  @Input() appName: string;
  @Input() ports: string[];
  @Input() labels: string[];
  @Input() appId: string;
  @Input() instanceTag: string;
  @Input() instanceActiveTag: string;

  public INITIAL_SORT_COLUMN = 'port';
  public INITIAL_SORT_DIRECTION = 'asc';

  public displayedColumns: string[] = [
    'description',
    'portState',
    'port',
    'rating',
  ];
  public dataSource: MatTableDataSource<Row> = new MatTableDataSource<Row>([]);

  @ViewChild(MatSort)
  sort: MatSort;

  loading = true;

  constructor(private instanceService: InstanceService, private processService: ProcessService) { }

  ngOnInit(): void {
    this.reload();
  }

  ngAfterViewInit(): void {
    this.dataSource.sort = this.sort;
    this.dataSource.data = [];
  }

  reload() {
    this.instanceService.getOpenPorts(this.instanceGroup, this.instanceId, this.minionName, this.ports).pipe(finalize(() => this.loading = false)).subscribe(r => {
      const rows: Row[] = [];
      for (let i = 0; i < this.ports.length; ++i) {
        rows.push({
          port: this.ports[i],
          description: this.labels[i],
          state: r[this.ports[i]],
          rating: this.isRatingOk(r[this.ports[i]])
        });
      }
      this.dataSource.data = rows;
    });
  }

  isRunning() {
    const state = this.processService.getStatusOfApp(this.appId)?.processState;
    return state === ProcessState.RUNNING || state === ProcessState.RUNNING_UNSTABLE;
  }

  isRatingOk(portState: boolean) {
    if (this.isRunning()) {
      return portState;
    } else {
      return !portState;
    }
  }
}
