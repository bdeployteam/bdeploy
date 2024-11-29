import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { OperatingSystem, UploadInfoDto } from 'src/app/models/gen.dtos';
import {
  ImportState,
  ImportStatus,
  UploadService,
  UploadState,
  UploadStatus,
  UrlParameter,
} from '../../services/upload.service';

@Component({
    selector: 'app-bd-file-upload-raw',
    templateUrl: './bd-file-upload-raw.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false
})
export class BdFileUploadRawComponent implements OnInit {
  @Input() file: File;
  @Input() uploadUrl: string;
  @Input() importUrl: string;
  @Input() parameters: UrlParameter[];
  @Input() formDataParam = 'file';

  @Output() dismiss = new EventEmitter<File>();

  private readonly uploads = inject(UploadService);

  protected uploadStatus: UploadStatus;
  protected importStatus: ImportStatus;
  protected isUploadFinished$ = new BehaviorSubject<boolean>(false);
  protected isUploadFailed$ = new BehaviorSubject<boolean>(false);
  protected isUploading$ = new BehaviorSubject<boolean>(false);
  protected isUploadProcessing$ = new BehaviorSubject<boolean>(false);
  protected isImporting$ = new BehaviorSubject<boolean>(false);
  protected isImportFinished$ = new BehaviorSubject<boolean>(false);
  protected isImportFailed$ = new BehaviorSubject<boolean>(false);
  protected icon$ = new BehaviorSubject<string>(this.getIcon());
  protected header$ = new BehaviorSubject<string>(this.getHeader());

  protected set supportAllOs(b: boolean) {
    const dto: UploadInfoDto = this.uploadStatus?.detail;
    if (dto) {
      if (b) {
        dto.supportedOperatingSystems = undefined;
      } else {
        dto.supportedOperatingSystems = [];
      }
    }
  }

  protected get supportAllOs(): boolean {
    const dto: UploadInfoDto = this.uploadStatus?.detail;
    return !!dto && dto.supportedOperatingSystems === undefined;
  }

  protected set supportWindows(b: boolean) {
    const dto: UploadInfoDto = this.uploadStatus?.detail;
    if (dto) {
      if (b) {
        if (dto.supportedOperatingSystems === undefined) {
          dto.supportedOperatingSystems = [OperatingSystem.WINDOWS];
        } else if (!dto.supportedOperatingSystems.includes(OperatingSystem.WINDOWS)) {
          dto.supportedOperatingSystems.push(OperatingSystem.WINDOWS);
        }
      } else if (dto.supportedOperatingSystems !== undefined) {
        dto.supportedOperatingSystems = dto.supportedOperatingSystems.filter((e) => e !== OperatingSystem.WINDOWS);
      }
    }
  }

  protected get supportWindows(): boolean {
    const dto: UploadInfoDto = this.uploadStatus?.detail;
    return !!dto && !!dto.supportedOperatingSystems && dto.supportedOperatingSystems.includes(OperatingSystem.WINDOWS);
  }

  protected set supportLinux(b: boolean) {
    const dto: UploadInfoDto = this.uploadStatus?.detail;
    if (dto) {
      if (b) {
        if (dto.supportedOperatingSystems === undefined) {
          dto.supportedOperatingSystems = [OperatingSystem.LINUX];
        } else if (!dto.supportedOperatingSystems.includes(OperatingSystem.LINUX)) {
          dto.supportedOperatingSystems.push(OperatingSystem.LINUX);
        }
      } else if (dto.supportedOperatingSystems !== undefined) {
        dto.supportedOperatingSystems = dto.supportedOperatingSystems.filter((e) => e !== OperatingSystem.LINUX);
      }
    }
  }

  protected get supportLinux(): boolean {
    const dto: UploadInfoDto = this.uploadStatus?.detail;
    return !!dto && !!dto.supportedOperatingSystems && dto.supportedOperatingSystems.includes(OperatingSystem.LINUX);
  }

  ngOnInit(): void {
    this.uploadStatus = this.uploads.uploadFile(this.uploadUrl, this.file, this.parameters, this.formDataParam);

    this.uploadStatus.stateObservable.pipe(finalize(() => this.uploadDone())).subscribe(() => {
      this.setProcessDetails();
    });
    this.uploadStatus.progressObservable.subscribe(() => {
      this.setProcessDetails();
    });
  }

  private isUserInputRequired(): boolean {
    return (
      !!this.uploadStatus.detail &&
      !(!!this.uploadStatus?.detail?.isHive || !!this.uploadStatus?.detail?.isProduct) &&
      !this.importStatus
    );
  }

  uploadDone() {
    this.uploadStatus.detail.filename = this.file.name;
    if (this.uploadStatus.detail.supportedOperatingSystems === null) {
      this.uploadStatus.detail.supportedOperatingSystems = []; // == unset
    }
    if (!this.isUserInputRequired()) {
      this.import();
    }
  }

  import() {
    this.importStatus = this.uploads.importFile(this.importUrl, this.uploadStatus.detail);
    this.importStatus.stateObservable.subscribe(() => {
      this.setProcessDetails();
    });
  }

  private setProcessDetails() {
    this.isUploadFinished$.next(this.uploadStatus?.state === UploadState.FINISHED);
    this.isUploadFailed$.next(this.uploadStatus?.state === UploadState.FAILED);
    this.isUploading$.next(this.uploadStatus?.state === UploadState.UPLOADING);
    this.isUploadProcessing$.next(this.uploadStatus?.state === UploadState.PROCESSING);
    this.isImporting$.next(this.importStatus?.state === ImportState.IMPORTING);
    this.isImportFinished$.next(this.importStatus?.state === ImportState.FINISHED);
    this.isImportFailed$.next(this.importStatus?.state === ImportState.FAILED);
    this.icon$.next(this.getIcon());
    this.header$.next(this.getHeader());
  }

  private getIcon() {
    if (this.isUploading$.value || this.isUploadProcessing$.value) {
      return 'cloud_upload';
    } else if (this.isUploadFinished$.value) {
      return 'cloud_done';
    } else if (this.isUploadFailed$.value) {
      return 'cloud_off';
    } else {
      return 'help';
    }
  }

  private getHeader() {
    if (this.isUploading$.value || this.isUploadProcessing$.value) {
      return `Uploading: ${this.file.name}`;
    } else if (this.isImporting$.value) {
      return `Importing: ${this.file.name}`;
    } else if (this.isUploadFinished$.value) {
      return `Success: ${this.file.name}`;
    } else if (this.isUploadFailed$.value) {
      return `Failed: ${this.file.name}`;
    } else {
      return 'Unknown State';
    }
  }

  protected onDismiss() {
    if (this.isUploading$.value) {
      this.uploadStatus.cancel();
    }
    this.dismiss.emit(this.file);
  }
}
