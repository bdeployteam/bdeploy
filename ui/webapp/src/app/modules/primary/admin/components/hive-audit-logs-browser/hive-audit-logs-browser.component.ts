import { Component, OnInit } from '@angular/core';
import { MatSlideToggleChange } from '@angular/material/slide-toggle';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';
import { AuditLogDto } from 'src/app/models/gen.dtos';
import { Logger, LoggingService } from 'src/app/modules/core/services/logging.service';
import { AuditService } from '../../services/audit.service';
import { HiveService } from '../../services/hive.service';
import { AuditLogDataProvider } from '../audit-log/audit-log.component';

export class HiveAuditLogDataProvider implements AuditLogDataProvider {
  constructor(private auditService: AuditService, private hive: string, private allColumns: boolean) {}

  showAllColumns(): boolean {
    return this.allColumns;
  }

  load(limit: number): Observable<AuditLogDto[]> {
    return this.auditService.hiveAuditLog(this.hive, undefined, limit);
  }

  loadMore(lastInstant: number, limit: number): Observable<AuditLogDto[]> {
    return this.auditService.hiveAuditLog(this.hive, lastInstant, limit);
  }
}

@Component({
  selector: 'app-hive-audit-logs-browser',
  templateUrl: './hive-audit-logs-browser.component.html',
  styleUrls: ['./hive-audit-logs-browser.component.css'],
})
export class HiveAuditLogsBrowserComponent implements OnInit {
  private readonly log: Logger = this.loggingService.getLogger('ServerAuditLogsComponent');

  hiveKeys: string[] = [];
  _selectedHive: string;
  set selectedHive(hive: string) {
    this._selectedHive = hive;
    this.refresh();
  }
  get selectedHive(): string {
    return this._selectedHive;
  }

  public showAllColumns = false;

  dataProvider: AuditLogDataProvider = null;

  constructor(
    private auditService: AuditService,
    private hiveService: HiveService,
    private route: ActivatedRoute,
    private loggingService: LoggingService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      this.selectedHive = params.get('hive');
    });

    this.hiveService.listHives().subscribe((keys) => {
      this.hiveKeys = keys;
      if (this.selectedHive == null && keys.length > 0) {
        this.selectedHive = keys[0];
      }
    });
  }

  public toggleColumns(event: MatSlideToggleChange) {
    this.showAllColumns = event.checked;
    this.refresh();
  }

  public refresh() {
    if (this.selectedHive) {
      this.dataProvider = new HiveAuditLogDataProvider(this.auditService, this.selectedHive, this.showAllColumns);
    }
  }
}
