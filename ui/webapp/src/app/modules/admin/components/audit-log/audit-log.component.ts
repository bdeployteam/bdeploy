import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import {
  Component,
  Input,
  OnChanges,
  OnInit,
  SimpleChange,
  TemplateRef,
  ViewChild,
  ViewContainerRef,
} from '@angular/core';
import { MatTable } from '@angular/material/table';
import { format } from 'date-fns';
import { Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AuditLogDto } from 'src/app/models/gen.dtos';
import { Logger, LoggingService } from 'src/app/modules/core/services/logging.service';

export interface AuditLogDataProvider {
  showAllColumns(): boolean;
  load(limit: number): Observable<AuditLogDto[]>;
  loadMore(lastInstant: number, limit: number): Observable<AuditLogDto[]>;
}

@Component({
  selector: 'app-audit-log',
  templateUrl: './audit-log.component.html',
  styleUrls: ['./audit-log.component.css'],
})
export class AuditLogComponent implements OnInit, OnChanges {
  public static LINE_LIMIT_DEFAULT = 1000;
  public static MAX_TEXT_LENGTH = 200;

  public log: Logger = this.loggingService.getLogger('AuditLogComponent');

  @ViewChild(MatTable) table: MatTable<any>;

  @Input()
  public dataProvider: AuditLogDataProvider;

  public data: AuditLogDto[] = [];
  public loading = false;
  public loadingMore = false;
  public noMoreData = false;

  public displayedColumnsAll: string[] = [
    'instant',
    'level',
    'endOfBatch',
    'thread',
    'threadId',
    'threadPriority',
    'who',
    'method',
    'what',
    'message',
    'parameters',
  ];
  public displayedColumnsCompact: string[] = ['instant', 'level', 'who', 'method', 'what', 'message', 'parameters'];

  private overlayRef: OverlayRef;
  public fullTextContent = '';

  constructor(
    private loggingService: LoggingService,
    private overlay: Overlay,
    private viewContainerRef: ViewContainerRef
  ) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnChanges(changes: { [propKey: string]: SimpleChange }): void {
    // only one single @Input()
    this.load();
  }

  public load(): void {
    this.data = [];
    this.noMoreData = false;
    if (this.dataProvider) {
      this.loading = true;
      this.dataProvider
        .load(AuditLogComponent.LINE_LIMIT_DEFAULT)
        .pipe(finalize(() => (this.loading = false)))
        .subscribe((data) => {
          this.noMoreData = data.length < AuditLogComponent.LINE_LIMIT_DEFAULT;
          this.data = data.reverse();
        });
    }
  }

  public loadMore(limit: number): void {
    if (this.dataProvider) {
      this.loadingMore = true;
      const lastInstant = this.data[this.data.length - 1].instant;
      this.dataProvider
        .loadMore(lastInstant, limit)
        .pipe(finalize(() => (this.loadingMore = false)))
        .subscribe((data) => {
          this.noMoreData = data.length < limit;
          this.data.push(...data.reverse());
          this.table.renderRows();
        });
    }
  }

  public getDisplayedColumns(): string[] {
    if (this.dataProvider && this.dataProvider.showAllColumns()) {
      return this.displayedColumnsAll;
    } else {
      return this.displayedColumnsCompact;
    }
  }

  public formatInstant(instant: number) {
    return instant === 0 ? '' : format(new Date(instant), 'dd.MM.yyyy HH:mm:ss.SSS');
  }

  public isLongText(text: string): boolean {
    return text && text.length > AuditLogComponent.MAX_TEXT_LENGTH;
  }

  public formatLongText(text: string): string {
    return this.isLongText(text) ? text.substr(0, AuditLogComponent.MAX_TEXT_LENGTH) : text;
  }

  public openOverlay(text: string, template: TemplateRef<any>): void {
    if (!this.isLongText(text)) {
      return;
    }
    this.closeOverlay();
    this.fullTextContent = text;
    this.overlayRef = this.overlay.create({
      positionStrategy: this.overlay.position().global().centerHorizontally().centerVertically(),
      hasBackdrop: true,
      disposeOnNavigation: true,
    });
    this.overlayRef.backdropClick().subscribe(() => this.closeOverlay());

    const portal = new TemplatePortal(template, this.viewContainerRef);
    this.overlayRef.attach(portal);
  }

  closeOverlay() {
    this.fullTextContent = '';
    if (this.overlayRef) {
      this.overlayRef.detach();
      this.overlayRef.dispose();
      this.overlayRef = null;
    }
  }
}
