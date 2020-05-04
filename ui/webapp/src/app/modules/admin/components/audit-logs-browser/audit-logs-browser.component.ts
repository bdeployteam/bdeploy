import { Component, OnInit } from '@angular/core';
import { MatSlideToggleChange } from '@angular/material/slide-toggle';
import { Observable } from 'rxjs';
import { AuditLogDto } from 'src/app/models/gen.dtos';
import { AuditService } from '../../services/audit.service';
import { AuditLogDataProvider } from '../audit-log/audit-log.component';

export class MinionAuditLogDataProvider implements AuditLogDataProvider {

  constructor(private auditService: AuditService, private allColumns: boolean) { }

  showAllColumns(): boolean {
    return this.allColumns;
  }

  load(limit: number): Observable<AuditLogDto[]> {
    return this.auditService.auditLog(undefined, limit);
  }

  loadMore(lastInstant: number, limit: number): Observable<AuditLogDto[]> {
    return this.auditService.auditLog(lastInstant, limit)
  }
}

@Component({
  selector: 'app-audit-logs-browser',
  templateUrl: './audit-logs-browser.component.html',
  styleUrls: ['./audit-logs-browser.component.css']
})
export class AuditLogsBrowserComponent implements OnInit {

  dataProvider: AuditLogDataProvider = null;
  public showAllColumns = false;

  constructor(private auditService: AuditService) { }

  ngOnInit(): void {
    this.refresh();
  }

  public toggleColumns(event: MatSlideToggleChange) {
    this.showAllColumns = event.checked;
    this.refresh();
  }

  public refresh() {
    this.dataProvider = new MinionAuditLogDataProvider(this.auditService, this.showAllColumns);
  }

}
