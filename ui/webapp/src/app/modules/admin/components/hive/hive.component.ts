import { CdkTable } from '@angular/cdk/table';
import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { finalize } from 'rxjs/operators';
import { HiveEntryDto, TreeEntryType } from '../../../../models/gen.dtos';
import { Logger, LoggingService } from '../../../core/services/logging.service';
import { MessageBoxMode } from '../../../shared/components/messagebox/messagebox.component';
import { DownloadService } from '../../../shared/services/download.service';
import { MessageboxService } from '../../../shared/services/messagebox.service';
import { compareTags } from '../../../shared/utils/manifest.utils';
import { HiveService } from '../../services/hive.service';

@Component({
  selector: 'app-hive',
  templateUrl: './hive.component.html',
  styleUrls: ['./hive.component.css'],
})
export class HiveComponent implements OnInit {
  public FILE_SIZE_LIMIT = 100000; // non-static because referenced in template

  log: Logger = this.loggingService.getLogger('HiveComponent');

  @Input('hive')
  set hive(hive: string) {
    this.selectHive(hive);
  }

  public displayedColumns: string[] = [
    'type',
    'name',
    'size',
    'delete',
    'download',
  ];

  public _hive: string;
  public longRunningOperation = false;
  public paths: { [key: string]: HiveEntryDto[] } = {};
  public entries: HiveEntryDto[];

  private asciiTypes = ['json', 'xml', 'yaml', 'bat', 'sh', 'txt', 'asc'];

  public fileEntry: HiveEntryDto;
  public fileContent: any;
  public fileContentLoading = false;

  @ViewChild('hivetable', { static: true }) hivetable: CdkTable<any>;

  constructor(
    private hiveService: HiveService,
    private loggingService: LoggingService,
    private snackbarService: MatSnackBar,
    private mbService: MessageboxService,
    private dlService: DownloadService
  ) {}

  ngOnInit() {
    this.setEntries([]);
    this.selectTop();
  }

  private setEntries(entries: HiveEntryDto[]): void {
    // sort by name ascending + sort by tag descending (newest first)
    this.entries = entries.sort((a, b) => {
      if (this.isManifest(a) && this.isManifest(b)) {
        const _a = a.mName ? a.mName : a.name;
        const _b = b.mName ? b.mName : b.name;
        const c = _a.localeCompare(_b);
        if (c === 0) {
          return -1 * compareTags(a.mTag, b.mTag);
        }
        return c;
      } else if (a.type === b.type) {
        // Tree or Blob
        return a.name.localeCompare(b.name);
      } else if (this.isManifest(a)) {
        return -1;
      } else if (this.isTree(a)) {
        if (this.isManifest(b)) {
          return 1;
        } else if (this.isBlob(b)) {
          return -1;
        }
      } else {
        return 1;
      }
      return 0;
    });
  }

  public selectHive(hive: string): void {
    this.log.debug('selectHive("' + hive + '")');
    this._hive = hive;
    const path: HiveEntryDto[] = this.paths[this._hive];
    if (path == null || path.length === 0) {
      this.selectTop();
    } else {
      this.selectHistory(path.length - 1);
    }
  }

  public selectTop(): void {
    this.log.debug('selectTop()');
    this.closeFile();
    this.paths[this._hive] = [];
    if (this._hive == null) {
      this.setEntries([]);
    } else {
      this.hiveService.listManifests(this._hive).subscribe((entries) => {
        this.setEntries(entries);
      });
    }
  }

  public selectHistory(index: number): void {
    this.log.debug('selectHistory(' + index + ')');
    this.closeFile();
    const entry: HiveEntryDto = this.paths[this._hive][index];
    this.paths[this._hive] = this.paths[this._hive].slice(0, index);
    this.selectRow(entry);
  }

  public selectRow(entry: HiveEntryDto) {
    this.log.debug('selectRow(' + JSON.stringify(entry) + ')');
    if (this.isManifest(entry)) {
      this.hiveService
        .listManifest(this._hive, entry.mName, entry.mTag)
        .subscribe((entries) => {
          this.paths[this._hive].push(entry);
          this.setEntries(entries);
        });
    } else if (this.isTree(entry)) {
      this.hiveService.list(this._hive, entry.id).subscribe((entries) => {
        this.paths[this._hive].push(entry);
        this.setEntries(entries);
      });
    } else if (
      this.isBlob(entry) &&
      this.asciiTypes.indexOf(this.getExtension(entry.name).toLowerCase()) >= 0
    ) {
      this.fileEntry = entry;
      if (entry.size <= this.FILE_SIZE_LIMIT) {
        this.loadFileContent(entry);
      }
    }
  }

  public closeFile(): void {
    this.fileEntry = undefined;
    this.fileContent = undefined;
  }

  public loadFileContent(entry: HiveEntryDto): void {
    this.fileContentLoading = true;
    this.hiveService
      .downloadAscii(this._hive, entry.id)
      .pipe(finalize(() => (this.fileContentLoading = false)))
      .subscribe((content) => {
        this.fileContent = content;
      });
  }

  public download(entry: HiveEntryDto): void {
    this.log.debug('download(' + JSON.stringify(entry) + ')');
    const targetFilename =
      entry.name +
      (this.isManifest(entry) || this.isTree(entry) ? '.json' : '');

    if (this.isManifest(entry)) {
      this.hiveService
        .downloadManifest(this._hive, entry.mName, entry.mTag)
        .subscribe((data) => this.downloadFile(targetFilename, data));
    } else {
      this.hiveService
        .download(this._hive, entry.id)
        .subscribe((data) => this.downloadFile(targetFilename, data));
    }
  }

  private downloadFile(filename: string, data: Blob): void {
    this.log.debug(
      'downloadFile("' + filename + '", <blob data: ' + data.size + ' bytes>)'
    );

    let mediatype = 'application/octet-stream';
    if (filename.endsWith('.json')) {
      mediatype = 'application/json';
    }
    const blob = new Blob([data], { type: mediatype });
    this.dlService.downloadBlob(filename, blob);
  }

  delete(entry: HiveEntryDto) {
    if (!this.isTopLevelManifest(entry)) {
      this.log.errorWithGuiMessage('Can only delete manifests');
      return;
    }

    this.mbService
      .open({
        title: 'Delete Manifest',
        message:
          'Really delete <strong>' +
          entry.mName +
          ':' +
          entry.mTag +
          '</strong>? This cannot be undone!' +
          '<br><br><strong>ATTENTION</strong>: There is no check whether this manifest is still referenced.',
        mode: MessageBoxMode.QUESTION,
      })
      .subscribe((r) => {
        if (r === true) {
          this.hiveService
            .delete(this._hive, entry.mName, entry.mTag)
            .subscribe((e) => {
              this.snackbarService.open(
                `Manifest ${entry.mName}:${entry.mTag} has been deleted.`,
                'DISMISS'
              );
              const index = this.entries.findIndex(
                (x) => x.mName === entry.mName && x.mTag === entry.mTag
              );
              this.entries.splice(index, 1);
              this.hivetable.renderRows();
            });
        }
      });
  }

  private startLongRunning() {
    this.longRunningOperation = true;
  }

  private finishLongRunning() {
    this.longRunningOperation = false;
  }

  pruneHive() {
    if (this._hive == null) {
      return;
    }
    this.startLongRunning();
    this.hiveService
      .prune(this._hive)
      .pipe(finalize(() => this.finishLongRunning()))
      .subscribe((r) => {
        this.log.info(
          `prune on ${this._hive} completed successfully, freeing ${r}`
        );
        this.snackbarService.open(
          `Prune freed ${r} on ${this._hive}.`,
          'DISMISS',
          { duration: 10000 }
        );
      });
  }

  fsckHive(fix: boolean) {
    if (this._hive == null) {
      return;
    }
    this.startLongRunning();
    this.hiveService
      .fsck(this._hive, fix)
      .pipe(finalize(() => this.finishLongRunning()))
      .subscribe((r) => {
        this.log.info(`fsck on ${this._hive} completed successfully`);
        this.snackbarService.open(
          `Check found ${Object.keys(r).length} damaged elements.`,
          'DISMISS',
          {
            duration: 10000,
          }
        );
      });
  }

  public getIcon(entry: HiveEntryDto): string {
    if (this.isManifest(entry)) {
      if (entry.mName == null) {
        return 'error';
      }
      return 'folder_special';
    } else if (this.isTree(entry)) {
      return 'folder';
    } else if (this.isBlob(entry)) {
      return 'view_headline';
    }
    return 'help_outline';
  }

  public isTopLevelManifest(entry: HiveEntryDto): boolean {
    return this.isManifest(entry) && this.paths[this._hive].length === 0;
  }

  public isManifest(entry: HiveEntryDto): boolean {
    return entry.type === TreeEntryType.MANIFEST;
  }

  public isTree(entry: HiveEntryDto): boolean {
    return entry.type === TreeEntryType.TREE;
  }

  public isBlob(entry: HiveEntryDto): boolean {
    return entry.type === TreeEntryType.BLOB;
  }

  public getCssClass(row): string {
    if (this.isBlob(row)) {
      return 'td-default';
    } else {
      return 'td-clickable';
    }
  }

  private getExtension(filename: string): string {
    const dotidx = filename.indexOf('.');
    return dotidx < 0 ? '' : filename.substr(dotidx + 1);
  }

  public back() {
    if (this.fileEntry) {
      this.closeFile();
    } else {
      const length = this.paths[this._hive].length;
      if (length > 1) {
        const entry: HiveEntryDto = this.paths[this._hive][length - 2];
        this.paths[this._hive] = this.paths[this._hive].slice(0, length - 2);
        this.selectRow(entry);
      } else if (length > 0) {
        this.selectTop();
      }
    }
  }
}
