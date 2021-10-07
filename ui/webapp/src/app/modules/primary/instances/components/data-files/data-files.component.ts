import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { BehaviorSubject, of } from 'rxjs';
import { finalize, first, skipWhile, tap } from 'rxjs/operators';
import { BdDataColumn, BdDataGrouping } from 'src/app/models/data';
import { FileStatusDto, FileStatusType, RemoteDirectory, RemoteDirectoryEntry } from 'src/app/models/gen.dtos';
import { BdDataDateCellComponent } from 'src/app/modules/core/components/bd-data-date-cell/bd-data-date-cell.component';
import { ACTION_CANCEL, ACTION_OK } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { BdFormInputComponent } from 'src/app/modules/core/components/bd-form-input/bd-form-input.component';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { formatSize } from 'src/app/modules/core/utils/object.utils';
import { ServersService } from '../../../servers/services/servers.service';
import { DataFilesService } from '../../services/data-files.service';
import { InstancesService } from '../../services/instances.service';

interface FileListEntry {
  directory: RemoteDirectory;
  entry: RemoteDirectoryEntry;
}

const colPath: BdDataColumn<FileListEntry> = {
  id: 'path',
  name: 'File Path',
  data: (r) => r.entry.path,
};

const colSize: BdDataColumn<FileListEntry> = {
  id: 'size',
  name: 'File Size',
  data: (r) => formatSize(r.entry.size),
  width: '100px',
  showWhen: '(min-width: 700px)',
};

const colModTime: BdDataColumn<FileListEntry> = {
  id: 'lastMod',
  name: 'Last Modification',
  data: (r) => r.entry.lastModified,
  width: '150px',
  showWhen: '(min-width: 800px)',
  component: BdDataDateCellComponent,
};

@Component({
  selector: 'app-data-files',
  templateUrl: './data-files.component.html',
  styleUrls: ['./data-files.component.css'],
})
export class DataFilesComponent implements OnInit {
  private readonly colDelete: BdDataColumn<FileListEntry> = {
    id: 'delete',
    name: 'Delete',
    data: (r) => 'Delete File',
    action: (r) => this.doDelete(r),
    icon: (r) => 'delete',
    width: '40px',
  };

  private readonly colDownload: BdDataColumn<FileListEntry> = {
    id: 'download',
    name: 'Downl.',
    data: (r) => 'Download File',
    action: (r) => this.doDownload(r),
    icon: (r) => 'cloud_download',
    width: '40px',
  };

  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ records$ = new BehaviorSubject<FileListEntry[]>(null);
  /* template */ noactive$ = new BehaviorSubject<boolean>(false);
  /* template */ columns: BdDataColumn<FileListEntry>[] = [colPath, colModTime, colSize, this.colDelete, this.colDownload];
  /* template */ grouping: BdDataGrouping<FileListEntry>[] = [{ definition: { group: (r) => r.directory.minion, name: 'Node Name' }, selected: [] }];
  /* template */ getRecordRoute = (row: FileListEntry) => {
    return ['', { outlets: { panel: ['panels', 'instances', 'data-files', row.directory.minion, row.entry.path] } }];
  };

  /* template */ tempFilePath: string;
  /* template */ tempFileMinion: string;
  /* template */ tempFileError: string;
  /* template */ tempFileContentLoading$ = new BehaviorSubject<boolean>(false);
  private tempFileContent = '';

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild('tempFileInput', { static: false }) private tempFileInput: BdFormInputComponent;

  constructor(public cfg: ConfigService, public instances: InstancesService, public servers: ServersService, private df: DataFilesService) {}

  ngOnInit(): void {
    this.load();

    this.servers.servers$.subscribe((_) => {
      this.load();
    });

    this.df.directories$.subscribe((dd) => {
      if (!dd) {
        this.records$.next(null);
        return;
      }

      const entries: FileListEntry[] = [];
      for (const dir of dd) {
        if (!!dir.problem) {
          console.warn(`Problem reading files from ${dir.minion}: ${dir.problem}`);
          continue;
        }
        for (const entry of dir.entries) {
          entries.push({ directory: dir, entry: entry });
        }
      }
      this.records$.next(entries);
      this.loading$.next(false);
    });
  }

  /* template */ load() {
    this.instances.current$
      .pipe(
        skipWhile((i) => !i),
        first()
      )
      .subscribe((i) => {
        this.noactive$.next(!i?.activeVersion?.tag);

        if (this.noactive$.value || !this.servers.isSynchronized(i.managedServer)) {
          this.loading$.next(false);
          return;
        }

        this.loading$.next(true);
        this.df.load();
      });
  }

  private doDelete(r: FileListEntry) {
    this.dialog
      .confirm(
        `Delete ${r.entry.path}?`,
        `The file <strong>${r.entry.path}</strong> on node <strong>${r.directory.minion}</strong> will be deleted permanently.`,
        'delete'
      )
      .subscribe((confirm) => {
        if (confirm) {
          this.df.deleteFile(r.directory, r.entry).subscribe((_) => {
            this.load();
          });
        }
      });
  }

  private doDownload(r: FileListEntry) {
    this.instances.download(r.directory, r.entry);
  }

  /* template */ getMinions(): string[] {
    return this.df.directories$.value.map((d) => d.minion);
  }

  /* template */ doAddFile(tpl: TemplateRef<any>): void {
    this.tempFilePath = '';
    this.tempFileContent = '';
    this.tempFileMinion = null;

    this.dialog
      .message({
        header: 'Add Configuration File',
        icon: 'add',
        template: tpl,
        validation: () =>
          !this.tempFileInput ? false : !this.tempFileInput.isInvalid() && !!this.tempFileMinion?.length && !this.tempFileContentLoading$.value,
        actions: [ACTION_CANCEL, ACTION_OK],
      })
      .subscribe((r) => {
        if (!r) {
          return;
        }
        const f: FileStatusDto = {
          file: this.tempFilePath,
          type: FileStatusType.ADD,
          content: this.tempFileContent,
        };
        const dir = this.df.directories$.value.find((d) => d.minion === this.tempFileMinion);
        let confirmation = of(true);

        if (!!dir.entries.find((e) => e.path === this.tempFilePath)) {
          confirmation = this.dialog.confirm('File Exists', 'A file with the given name exists - replace?', 'warning').pipe(
            tap((r) => {
              if (r) f.type = FileStatusType.EDIT;
            })
          );
        }

        confirmation.subscribe((confirm) => {
          if (!confirm) return;

          this.df
            .updateFile(dir, f)
            .pipe(finalize(() => this.load()))
            .subscribe();
        });
      });
  }

  /* template */ doAddFileContent(file: File) {
    this.tempFileError = null;
    this.tempFileContentLoading$.next(true);

    if (file.size > 1024 * 1024 * 20) {
      this.tempFileContentLoading$.next(false);
      this.tempFileError = 'Selected File is too large, size limit 20MB';
      return;
    }

    const reader = new FileReader();
    reader.onload = (ev) => {
      const result = reader.result.toString();

      // always set the file name/path to the original dropped file name.
      this.tempFilePath = file.name;

      // extract the base64 part of the data URL...
      this.tempFileContent = result.substr(result.indexOf(',') + 1);
      this.tempFileContentLoading$.next(false);
    };
    reader.readAsDataURL(file);
  }
}
