import { Component, OnDestroy, TemplateRef, ViewChild } from '@angular/core';
import { Base64 } from 'js-base64';
import { BehaviorSubject, Subscription } from 'rxjs';
import {
  BdDataColumn,
  BdDataGrouping,
  BdDataGroupingDefinition,
} from 'src/app/models/data';
import { FileStatusType } from 'src/app/models/gen.dtos';
import {
  ACTION_CANCEL,
  ACTION_OK,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { BdFormInputComponent } from 'src/app/modules/core/components/bd-form-input/bd-form-input.component';
import { ConfigFilesColumnsService } from '../../../services/config-files-columns.service';
import {
  ConfigFile,
  ConfigFilesService,
} from '../../../services/config-files.service';

@Component({
  selector: 'app-config-files',
  templateUrl: './config-files.component.html',
})
export class ConfigFilesComponent implements OnDestroy {
  /* template */ records$ = new BehaviorSubject<ConfigFile[]>(null);
  /* template */ columns: BdDataColumn<ConfigFile>[] =
    this.cfgFileColumns.defaultColumns;

  /* template */ groupingDefinition: BdDataGroupingDefinition<ConfigFile> = {
    name: 'Configuration File Availability',
    group: (r) => this.getGroup(r),
  };

  /* template */ grouping: BdDataGrouping<ConfigFile>[] = [
    { definition: this.groupingDefinition, selected: [] },
  ];

  /* template */ tempFilePath: string;
  /* template */ tempFileError: string;
  /* template */ tempFileContentLoading$ = new BehaviorSubject<boolean>(false);
  private tempFileContent = '';
  private tempFileIsBin = false;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild('tempFileInput', { static: false })
  private tempFileInput: BdFormInputComponent;

  private subscription: Subscription;

  constructor(
    public cfgFiles: ConfigFilesService,
    public cfgFileColumns: ConfigFilesColumnsService
  ) {
    this.subscription = this.cfgFiles.files$.subscribe((f) => {
      this.records$.next(
        f?.filter((x) => x.modification?.type !== FileStatusType.DELETE)
      );
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

  /* template */ doAddFile(tpl: TemplateRef<any>): void {
    this.tempFilePath = '';
    this.tempFileContent = '';

    this.dialog
      .message({
        header: 'Add Configuration File',
        icon: 'add',
        template: tpl,
        validation: () =>
          !this.tempFileInput
            ? false
            : !this.tempFileInput.isInvalid() &&
              !this.tempFileContentLoading$.value,
        actions: [ACTION_CANCEL, ACTION_OK],
      })
      .subscribe((r) => {
        if (!r) {
          return;
        }
        this.cfgFiles.add(
          this.tempFilePath,
          this.tempFileContent,
          this.tempFileIsBin
        );
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
}
