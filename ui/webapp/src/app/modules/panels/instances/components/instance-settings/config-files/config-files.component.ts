import { Component, OnDestroy, OnInit, TemplateRef, ViewChild, inject } from '@angular/core';
import { Base64 } from 'js-base64';
import { BehaviorSubject, Subscription } from 'rxjs';
import { BdDataColumn, BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { FileStatusType } from 'src/app/models/gen.dtos';
import {
  ACTION_CANCEL,
  ACTION_OK,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { BdFormInputComponent } from 'src/app/modules/core/components/bd-form-input/bd-form-input.component';
import { ConfigFilesColumnsService } from '../../../services/config-files-columns.service';
import { ConfigFile, ConfigFilesService } from '../../../services/config-files.service';

@Component({
    selector: 'app-config-files',
    templateUrl: './config-files.component.html',
    standalone: false
})
export class ConfigFilesComponent implements OnInit, OnDestroy {
  protected readonly cfgFiles = inject(ConfigFilesService);
  protected readonly cfgFileColumns = inject(ConfigFilesColumnsService);

  protected records$ = new BehaviorSubject<ConfigFile[]>(null);
  protected readonly columns: BdDataColumn<ConfigFile>[] = this.cfgFileColumns.defaultColumns;

  protected groupingDefinition: BdDataGroupingDefinition<ConfigFile> = {
    name: 'Configuration File Availability',
    group: (r) => this.getGroup(r),
  };

  protected grouping: BdDataGrouping<ConfigFile>[] = [{ definition: this.groupingDefinition, selected: [] }];

  protected tempFilePath: string;
  protected tempFileError: string;
  protected tempFileContentLoading$ = new BehaviorSubject<boolean>(false);
  protected tempFileContent = '';
  private tempFileIsBin = false;

  protected bulkMode = false;
  protected bulkSelection$ = new BehaviorSubject<ConfigFile[]>([]);

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild('tempFileInput', { static: false }) private readonly tempFileInput: BdFormInputComponent;

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = this.cfgFiles.files$.subscribe((f) => {
      this.records$.next(f?.filter((x) => x.modification?.type !== FileStatusType.DELETE));
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private getGroup(r: ConfigFile) {
    if (!!r?.persistent?.instanceId || !!r?.modification?.file) {
      return 'Current instance configuration files';
    }

    if (r?.persistent?.productId) {
      return 'Files available from the current product version.';
    }
  }

  protected doAddFile(tpl: TemplateRef<unknown>): void {
    this.tempFilePath = '';
    this.tempFileContent = '';

    this.dialog
      .message({
        header: 'Add Configuration File',
        icon: 'add',
        template: tpl,
        validation: () =>
          !this.tempFileInput ? false : !this.tempFileInput.isInvalid() && !this.tempFileContentLoading$.value,
        actions: [ACTION_CANCEL, ACTION_OK],
      })
      .subscribe((r) => {
        if (!r) {
          return;
        }
        this.cfgFiles.add(this.tempFilePath, this.tempFileContent, this.tempFileIsBin);
      });
  }

  protected doAddFileContent(file: File) {
    this.tempFileError = null;
    this.tempFileContentLoading$.next(true);

    if (file.size > 1024 * 1024 * 20) {
      this.tempFileContentLoading$.next(false);
      this.tempFileError = 'Selected file is too large, size limit 20MB';
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result.toString();

      // always set the file name/path to the original dropped file name.
      this.tempFilePath = file.name;

      // extract the base64 part of the data URL...
      this.tempFileContent = result.substr(result.indexOf(',') + 1);

      const buffer = Base64.toUint8Array(this.tempFileContent);
      let isBinary = false;
      for (let i = 0; i < Math.max(4096, buffer.length); ++i) {
        if (buffer[i] === 0) {
          isBinary = true;
          break;
        }
      }
      this.tempFileIsBin = isBinary;
      this.tempFileContentLoading$.next(false);
    };
    reader.readAsDataURL(file);
  }

  protected bulkDelete() {
    const selected = this.bulkSelection$.value;
    this.dialog
      .confirm(
        `Delete ${selected.length} files?`,
        `This will remove ${selected.length} files from the current set of configuration files.`,
      )
      .subscribe((r) => {
        if (r) {
          selected.map((file) => this.cfgFiles.getPath(file)).forEach((path) => this.cfgFiles.delete(path));
        }
      });
  }

  protected checkChangeForbidden(file: ConfigFile): boolean {
    return !file?.persistent?.instanceId && !file?.modification?.file;
  }
}
