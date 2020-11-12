import { Component, Inject, OnInit } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import ReconnectingWebSocket from 'reconnecting-websocket';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { OperatingSystem, UploadInfoDto } from 'src/app/models/gen.dtos';
import { ErrorMessage, LoggingService } from 'src/app/modules/core/services/logging.service';
import { ActivitySnapshotTreeNode, RemoteEventsService } from 'src/app/modules/shared/services/remote-events.service';
import { UploadState, UploadStatus } from 'src/app/modules/shared/services/upload.service';
import { ImportState, ImportStatus, SoftwareService } from '../../services/software.service';

enum STEPS {
  /** Initial step, drop/browse files to import */
  FILES = 'FILES',
  /** Background step, file upload is in progress */
  UPLOAD = 'UPLOAD',
  /** Upload done, input of missing data */
  DATA = 'DATA',
  /** Background step, import of uploaded files is in progress */
  IMPORT = 'IMPORT',
  /** Display import result  and close button */
  RESULT = 'RESULT',
}

const ALL_OS: OperatingSystem[] = [OperatingSystem.WINDOWS, OperatingSystem.LINUX];

@Component({
  selector: 'app-software-repo-file-upload',
  templateUrl: './software-repo-file-upload.component.html',
  styleUrls: ['./software-repo-file-upload.component.css'],
})
export class SoftwareRepoFileUploadComponent implements OnInit {
  private readonly log = this.loggingService.getLogger('FileUploadComponent');
  public operatingSystems = ALL_OS;
  public steps = STEPS;
  public uploadState = UploadState;
  public importState = ImportState;
  private ws: ReconnectingWebSocket;

  /** current wizard step */
  public step = STEPS.FILES;

  /** dropped files */
  public files: File[] = [];
  /** files upload info */
  public uploads: Map<string, UploadStatus>;
  public imports: Map<string, ImportStatus>;

  constructor(
    @Inject(MAT_DIALOG_DATA) public repositoryName: string,
    public dialogRef: MatDialogRef<SoftwareRepoFileUploadComponent>,
    private eventService: RemoteEventsService,
    private softwareService: SoftwareService,
    private loggingService: LoggingService
  ) {}

  ngOnInit(): void {
    // start event source - can't filter by narrow scope as there might be multiple uploads
    this.ws = this.eventService.createActivitiesWebSocket([]);
    this.ws.addEventListener('error', (err) => {
      this.log.errorWithGuiMessage(new ErrorMessage('Error while processing events', err));
    });
    this.ws.addEventListener('message', (e) => this.onEventReceived(e));
    this.dialogRef.disableClose = true;
  }

  onEventReceived(e: MessageEvent) {
    const blob = e.data as Blob;
    const r = new FileReader();
    r.onload = () => {
      // we accept all events as we don't know the scope we're looking for beforehand.
      const rootEvents = this.eventService.parseEvent(r.result, []);

      // each received event's root scope must match a scope of an UploadStatus object.
      // discard all events where this is not true.
      for (const event of rootEvents) {
        if (!event.snapshot || !event.snapshot.scope || event.snapshot.scope.length < 1) {
          continue;
        }

        const status: UploadStatus = this.uploads
          ? Array.from(this.uploads.values()).find((s) => s.scope == event.snapshot.scope[0])
          : null;
        if (!status) {
          continue; // discard, not ours.
        }

        // if we do have a match, extract the most relevant message, set it, and then flag a table repaint.
        status.processingHint = this.extractMostRelevantMessage(event);
      }
    };
    r.readAsText(blob);
  }

  extractMostRelevantMessage(node: ActivitySnapshotTreeNode): string {
    // recurse down, always pick the /last/ child.
    if (node.children && node.children.length > 0) {
      return this.extractMostRelevantMessage(node.children[node.children.length - 1]);
    }

    if (!node.snapshot) {
      return null;
    }

    if (node.snapshot.max <= 0) {
      if (node.snapshot.current > 0) {
        return `${node.snapshot.name} (${node.snapshot.current})`;
      } else {
        return node.snapshot.name;
      }
    } else {
      return `${node.snapshot.name} (${node.snapshot.current}/${node.snapshot.max})`;
    }
  }

  addFiles(fileList: FileList) {
    if (this.step !== STEPS.FILES) {
      return;
    }
    // add files
    for (let i = 0; i < fileList.length; i++) {
      this.files.push(fileList[i]);
    }
  }

  removeFile(file: File) {
    const idx = this.files.indexOf(file);
    this.files.splice(idx, 1);
    if (this.uploads) {
      this.uploads.delete(file.name);
    }
    if (this.imports) {
      this.imports.delete(file.name);
    }
    // go ahead if all failures are removed now
    if (this.uploads && this.canNextStep()) {
      this.onNextButtonPressed();
    }
  }

  getUploadStatus(file: File): UploadStatus {
    if (this.uploads === undefined) {
      return null;
    }
    return this.uploads.get(file.name);
  }

  isUploadState(file: File, state: UploadState) {
    const status = this.getUploadStatus(file);
    if (!status) {
      return false;
    }
    return status.state === state;
  }

  getImportStatus(file: File): ImportStatus {
    if (this.imports === undefined) {
      return null;
    }
    return this.imports.get(file.name);
  }

  isImportState(file: File, state: ImportState) {
    const status = this.getImportStatus(file);
    if (!status) {
      return false;
    }
    return status.state === state;
  }

  hasUploadFailures(): boolean {
    return this.uploads ? Array.from(this.uploads.values()).some((us) => us.state === UploadState.FAILED) : false;
  }

  hasImportFailures(): boolean {
    return this.imports ? Array.from(this.imports.values()).some((is) => is.state === ImportState.FAILED) : false;
  }

  isFileInQueue(file: File): boolean {
    return (
      !this.isFileInProgress(file) &&
      !this.isUploadState(file, this.uploadState.FINISHED) &&
      !this.isUploadState(file, this.uploadState.FAILED)
    );
  }

  isFileInProgress(file: File): boolean {
    if (this.isStep(STEPS.UPLOAD)) {
      return !(
        this.isUploadState(file, this.uploadState.FINISHED) || this.isUploadState(file, this.uploadState.FAILED)
      );
    } else if (this.isStep(STEPS.IMPORT)) {
      return !(this.isImportState(file, ImportState.FINISHED) || this.isImportState(file, ImportState.FAILED));
    }
    return false;
  }

  isFileUploaded(file: File): boolean {
    return (
      !this.isFileFailed(file) &&
      !this.getImportStatus(file) &&
      (this.isStep(STEPS.FILES) || this.isStep(STEPS.UPLOAD) || this.isStep(STEPS.DATA)) &&
      this.isUploadState(file, this.uploadState.FINISHED)
    );
  }

  isFileImported(file: File): boolean {
    return !this.isFileFailed(file) && this.isImportState(file, this.importState.FINISHED);
  }

  isFileFailed(file: File): boolean {
    return (
      (this.isStep(STEPS.FILES) && this.isUploadState(file, this.uploadState.FAILED)) ||
      (this.isStep(STEPS.DATA) && this.isImportState(file, this.importState.FAILED))
    );
  }

  getUploadProgress(file: File) {
    return this.getUploadStatus(file).progressObservable;
  }

  isStep(step: string): boolean {
    return this.step === STEPS[step];
  }

  canCancel(): boolean {
    return this.step !== STEPS.UPLOAD && this.step !== STEPS.IMPORT;
  }

  canNextStep(): boolean {
    if (this.step === STEPS.FILES) {
      return this.files?.length > 0;
    } else if (this.step === STEPS.DATA) {
      if (this.hasUploadFailures()) {
        return false;
      }
      const ui: UploadInfoDto[] = Array.from(this.uploads.values()).map((us) => us.detail);
      for (let i = 0; i < ui.length; i++) {
        if (!ui[i].isHive && !ui[i].isProduct) {
          const nameOk = ui[i].name?.trim().length > 0;
          const tagOk = ui[i].tag?.trim().length > 0;
          const osOk = ui[i].supportedOperatingSystems == undefined || ui[i].supportedOperatingSystems?.length > 0;
          if (!nameOk || !tagOk || !osOk) {
            return false;
          }
        }
      }
      return true;
    } else if (this.step === STEPS.RESULT) {
      return !this.hasImportFailures();
    }
    return false;
  }

  onNextButtonPressed(): void {
    switch (this.step) {
      case STEPS.FILES:
        this.step = STEPS.UPLOAD;
        this.doUpload();
        break;
      case STEPS.DATA:
        this.step = STEPS.IMPORT;
        this.doImport();
        break;
      case STEPS.RESULT:
        this.dialogRef.close();
        break;
      default:
        this.log.error('unxpected step!');
    }
  }

  doUpload() {
    if (!this.uploads) {
      // first upload
      this.uploads = this.softwareService.upload(this.repositoryName, this.files);
      forkJoin(Array.from(this.uploads.values()).map((us) => us.stateObservable))
        .pipe(finalize(() => this.uploadDone()))
        .subscribe();
    } else {
      // retry upload of failed
      const failedFiles = this.files.filter((f) => {
        const us: UploadStatus = this.getUploadStatus(f);
        return us === undefined || us.state === UploadState.FAILED;
      });
      const map: Map<string, UploadStatus> = this.softwareService.upload(this.repositoryName, failedFiles);
      map.forEach((v, k, m) => this.uploads.set(k, v));
      forkJoin(Array.from(map.values()).map((us) => us.stateObservable))
        .pipe(finalize(() => this.uploadDone()))
        .subscribe();
    }
  }

  uploadDone() {
    // transfer original filename to dtos for later reference
    this.uploads.forEach((v, k, m) => {
      if (v.state === this.uploadState.FINISHED) {
        v.detail.filename = k;
      }
    });

    if (this.hasUploadFailures()) {
      this.step = STEPS.FILES;
    } else {
      this.step = STEPS.DATA;
      Array.from(this.uploads.values()).forEach((us) => (us.detail.supportedOperatingSystems = [])); // ensure invalid/empty preset
    }
  }

  doImport() {
    const dtos: UploadInfoDto[] = Array.from(this.uploads.values()).map((us) => us.detail);

    if (!this.imports) {
      // first import
      dtos.forEach((dto) => (dto.details = undefined));
      this.imports = this.softwareService.import(this.repositoryName, dtos);
      forkJoin(Array.from(this.imports.values()).map((is) => is.stateObservable))
        .pipe(finalize(() => this.importDone()))
        .subscribe();
    } else {
      // retry import of failed
      const failedDtos = Array.from(this.imports.values())
        .filter((im) => im.state === ImportState.FAILED)
        .map((im) => this.uploads.get(im.filename).detail);
      if (failedDtos.length === 0) {
        // user removed all failed import files
        this.importDone();
      } else {
        failedDtos.forEach((dto) => (dto.details = undefined));
        const map: Map<string, ImportStatus> = this.softwareService.import(this.repositoryName, failedDtos);
        map.forEach((v, k, m) => this.imports.set(k, v));
        forkJoin(Array.from(map.values()).map((is) => is.stateObservable))
          .pipe(finalize(() => this.importDone()))
          .subscribe();
      }
    }
  }

  importDone() {
    if (this.hasImportFailures()) {
      this.step = STEPS.DATA;
    } else {
      this.step = STEPS.RESULT;
    }
  }

  getUploadInfo(file: File): UploadInfoDto {
    return this.uploads?.get(file.name)?.detail;
  }

  supportsAllOs(file: File) {
    const dto: UploadInfoDto = this.getUploadInfo(file);
    return dto && dto.supportedOperatingSystems === undefined;
  }

  onToggleAllOs(file: File) {
    const dto: UploadInfoDto = this.getUploadInfo(file);
    if (dto) {
      if (dto.supportedOperatingSystems === undefined) {
        // all OS
        dto.supportedOperatingSystems = [];
      } else {
        dto.supportedOperatingSystems = undefined;
      }
    }
  }

  supportsOs(file: File, os: OperatingSystem): boolean {
    const dto: UploadInfoDto = this.getUploadInfo(file);
    return dto && dto.supportedOperatingSystems && dto.supportedOperatingSystems.find((o) => o === os) !== undefined;
  }

  onToggleSupportOs(file: File, os: OperatingSystem) {
    const dto: UploadInfoDto = this.getUploadInfo(file);
    if (this.supportsOs(file, os)) {
      dto.supportedOperatingSystems = dto.supportedOperatingSystems.filter((o) => o !== os);
    } else {
      if (!dto.supportedOperatingSystems) {
        dto.supportedOperatingSystems = [];
      }
      dto.supportedOperatingSystems.push(os);
    }
  }

  getButtonText(): string {
    switch (this.step) {
      case STEPS.FILES:
        return this.uploads ? 'Retry Failed Upload(s)' : 'Upload';
      case STEPS.DATA:
        return this.imports ? 'Retry Failed Import(s)' : 'Import';
      case STEPS.RESULT:
        return 'Close';
    }
    return '...';
  }

  getHint(file: File) {
    if (this.isUploadState(file, this.uploadState.UPLOADING)) {
      return 'uploading...';
    } else if (this.isUploadState(file, this.uploadState.PROCESSING)) {
      return 'processing...';
    } else if (this.isImportState(file, this.importState.IMPORTING)) {
      return 'importing...';
    } else if (
      (this.isStep(STEPS.FILES) || this.isStep(STEPS.UPLOAD)) &&
      this.isUploadState(file, this.uploadState.FAILED)
    ) {
      return this.getUploadStatus(file).detail;
    }
    return this.getUploadInfo(file)?.details;
  }
}
