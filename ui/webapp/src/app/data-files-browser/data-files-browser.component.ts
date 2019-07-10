import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Location } from '@angular/common';
import { Component, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { PageEvent } from '@angular/material';
import { ActivatedRoute } from '@angular/router';
import { Observable, of } from 'rxjs';
import { InstanceDirectory, InstanceDirectoryEntry, StringEntryChunkDto } from '../models/gen.dtos';
import { InstanceService } from '../services/instance.service';
import { Logger, LoggingService } from '../services/logging.service';
import { MessageboxService } from '../services/messagebox.service';

@Component({
  selector: 'app-data-files-browser',
  templateUrl: './data-files-browser.component.html',
  styleUrls: ['./data-files-browser.component.css']
})
export class DataFilesBrowserComponent implements OnInit {

  private log: Logger = this.loggingService.getLogger('DataFilesBrowserComponent');

  groupParam: string = this.route.snapshot.paramMap.get('group');
  uuidParam: string = this.route.snapshot.paramMap.get('uuid');
  versionParam: string = this.route.snapshot.paramMap.get('version');

  public displayedColumns: string[] = ['icon', 'path', 'size', 'timestamp', 'download'];

  public pageEvents = new Map<string, PageEvent>();

  public instanceDirectories: InstanceDirectory[];
  public activeInstanceDirectory: InstanceDirectory = null;
  public activeInstanceDirectoryEntry: InstanceDirectoryEntry = null;

  public get instanceDirectoryNames(): string[] {
    return this.instanceDirectories ? this.instanceDirectories.map(id => id.minion) : [];
  }
  private overlayRef: OverlayRef;

  constructor(private overlay: Overlay,
    private viewContainerRef: ViewContainerRef,
    private route: ActivatedRoute,
    private instanceService: InstanceService,
    private loggingService: LoggingService,
    public location: Location,
    private messageBoxService: MessageboxService
  ) {}


  public ngOnInit(): void {
    this.reload();
  }

  public reload() {
    this.instanceService.listDataDirSnapshot(this.groupParam, this.uuidParam).subscribe(
      instanceDirectories =>  {
        console.log(JSON.stringify(instanceDirectories, null, '\t'));
        this.instanceDirectories = instanceDirectories.sort((a, b) => {
          if (a.minion === 'master') {
            return -1;
          } else if (b.minion === 'master') {
            return 1;
          } else {
            return a.minion.toLocaleLowerCase().localeCompare(b.minion.toLocaleLowerCase());
          }
        });
    });
  }

  public getInitialPageSize() {
    return 10;
  }

  public getInitialPageIndex() {
    return 0;
  }

  public getCurrentPage(instanceDirectory: InstanceDirectory) {
    const pageEvent = this.pageEvents.get(instanceDirectory.minion);

    const pageIndex = pageEvent ? pageEvent.pageIndex : this.getInitialPageIndex();
    const pageSize = pageEvent ? pageEvent.pageSize : this.getInitialPageSize();

    const firstIdx = pageIndex * pageSize;
    return instanceDirectory.entries.slice(firstIdx, firstIdx + pageSize);
  }

  public formatTimestamp(timestamp: number): string {
    return new Date(timestamp).toLocaleString();
  }

  public download(instanceDirectory: InstanceDirectory, instanceDirectoryEntry: InstanceDirectoryEntry) {
    this.instanceService.getContentChunk(this.groupParam, this.uuidParam, instanceDirectory, instanceDirectoryEntry, 0, 0, true).subscribe(
      dto => {
        this.downloadFile(instanceDirectoryEntry.path, dto.content);
      }
    );
  }

  private downloadFile(filename: string, data: string): void {
    const blob = new Blob([data], { type: 'text/plain' });

    const link = document.createElement('a');
    link.href = window.URL.createObjectURL(blob);
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }

  getCurrentOutputEntryFetcher(): () => Observable<InstanceDirectoryEntry> {
    return () => of(this.activeInstanceDirectoryEntry ? this.activeInstanceDirectoryEntry : null);
  }

  getOutputContentFetcher(): (offset: number, limit: number) => Observable<StringEntryChunkDto> {
    return (offset, limit) => {
      return this.instanceService.getContentChunk(this.groupParam, this.uuidParam, this.activeInstanceDirectory, this.activeInstanceDirectoryEntry, offset, limit, true);
    };
  }

  openOutputOverlay(instanceDirectory: InstanceDirectory, instanceDirectoryEntry: InstanceDirectoryEntry, template: TemplateRef<any>) {
    this.activeInstanceDirectory = instanceDirectory;
    this.activeInstanceDirectoryEntry = instanceDirectoryEntry;

    this.closeOutputOverlay();

    this.overlayRef = this.overlay.create({
      height: '90%',
      width: '90%',
      positionStrategy: this.overlay.position().global().centerHorizontally().centerVertically(),
      hasBackdrop: true
    });
    this.overlayRef.backdropClick().subscribe(() => this.closeOutputOverlay());

    const portal = new TemplatePortal(template, this.viewContainerRef);
    this.overlayRef.attach(portal);
  }

  closeOutputOverlay() {
    if (this.overlayRef) {
      this.activeInstanceDirectory = null;
      this.activeInstanceDirectoryEntry = null;
      this.overlayRef.detach();
      this.overlayRef.dispose();
      this.overlayRef = null;
    }
  }

}
