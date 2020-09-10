import {
  Component,
  ElementRef,
  Inject,
  OnInit,
  ViewChild
} from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import ReconnectingWebSocket from 'reconnecting-websocket';
import { forkJoin } from 'rxjs';
import { ManifestKey, OperatingSystem } from 'src/app/models/gen.dtos';
import {
  ErrorMessage,
  LoggingService
} from 'src/app/modules/core/services/logging.service';
import {
  ActivitySnapshotTreeNode,
  RemoteEventsService
} from 'src/app/modules/shared/services/remote-events.service';
import {
  UploadService,
  UploadState,
  UploadStatus,
  UrlParameter
} from 'src/app/modules/shared/services/upload.service';
import { SoftwareService } from '../../services/software.service';

const ALL_OS: OperatingSystem[] = [
  OperatingSystem.WINDOWS,
  OperatingSystem.LINUX,
  // currently unsupported, or support hidden.
  // OperatingSystem.MACOS,
  // OperatingSystem.AIX
];

class RepositoryUploadData {
  public hive = false;
  public name = '';
  public tag = '';
  public supportedOS = new Map<OperatingSystem, boolean>();
  constructor() {
    for (const os of ALL_OS) {
      this.supportedOS.set(os, false);
    }
  }
}

@Component({
  selector: 'app-software-repo-file-upload',
  templateUrl: './software-repo-file-upload.component.html',
  styleUrls: ['./software-repo-file-upload.component.css'],
})
export class SoftwareRepoFileUploadComponent implements OnInit {
  private readonly log = this.loggingService.getLogger('FileUploadComponent');

  public uploading = false;
  public cancelEnabled = true;
  public uploadEnabled = false;
  public uploadFinished = true;

  public buttonText = 'Upload';

  public uploadState = UploadState;
  private ws: ReconnectingWebSocket;

  @ViewChild('file', { static: true })
  public fileRef: ElementRef;

  /** Files to be uploaded */
  public uploads: Map<string, UploadStatus>;
  public fileData: RepositoryUploadData[] = [];
  public files: File[] = [];

  public operatingSystems = ALL_OS;

  constructor(
    @Inject(MAT_DIALOG_DATA) public repositoryName: string,
    public dialogRef: MatDialogRef<SoftwareRepoFileUploadComponent>,
    public uploadService: UploadService,
    private eventService: RemoteEventsService,
    private softwareService: SoftwareService,
    private loggingService: LoggingService
  ) {}

  ngOnInit(): void {
    // start event source - can't filter by narrow scope as there might be multiple uploads
    this.ws = this.eventService.createActivitiesWebSocket([]);
    this.ws.addEventListener('error', (err) => {
      this.log.errorWithGuiMessage(
        new ErrorMessage('Error while processing events', err)
      );
    });
    this.ws.addEventListener('message', (e) => this.onEventReceived(e));
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
        if (
          !event.snapshot ||
          !event.snapshot.scope ||
          event.snapshot.scope.length < 1
        ) {
          continue;
        }

        const status = this.getUploadStatusByUUID(event.snapshot.scope[0]);
        if (!status) {
          continue; // discard, not ours.
        }

        // if we do have a match, extract the most relevant message, set it, and then flag a table repaint.
        status.processingHint = this.extractMostRelevantMessage(event);
      }
    };
    r.readAsText(blob);
  }

  getUploadStatusByUUID(uuid: string): UploadStatus {
    if (this.uploads === undefined) {
      return null;
    }
    for (const s of Array.from(this.uploads.values())) {
      if (s.scope === uuid) {
        return s;
      }
    }
    return null;
  }

  extractMostRelevantMessage(node: ActivitySnapshotTreeNode): string {
    // recurse down, always pick the /last/ child.
    if (node.children && node.children.length > 0) {
      return this.extractMostRelevantMessage(
        node.children[node.children.length - 1]
      );
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
    // Clear all finished files when adding a new one
    if (this.uploads) {
      this.uploads.forEach((us) => {
        if (us.state === UploadState.FINISHED) {
          return;
        }
        this.files.splice(this.files.indexOf(us.file), 1);
        this.uploads.delete(us.file.name);
      });
    }
    // Reset internal upload state
    this.uploads = undefined;

    // add files
    for (let i = 0; i < fileList.length; i++) {
      this.files.push(fileList[i]);
      this.fileData.push(new RepositoryUploadData());
    }

    // Update dialog state
    this.buttonText = 'Upload';
    this.uploadFinished = false;
    this.uploadEnabled = this.files.length > 0;
    this.dialogRef.disableClose = this.files.length > 0;
  }

  onOkButtonPressed() {
    // Close dialog if all files are uploaded
    if (this.uploadFinished) {
      this.dialogRef.close();
      return;
    }

    const manifestParameter: UrlParameter[][] = [];
    const rawPackages: File[] = [];
    const toUpload: File[] = [];

    // check manifest data and
    for (let i = 0; i < this.files.length; i++) {
      const data = this.fileData[i];

      if (data.hive) {
        toUpload.push(this.files[i]);
        continue;
      }

      const os: OperatingSystem[] = [];
      for (const [key, value] of data.supportedOS.entries()) {
        if (value) {
          os.push(key);
        }
      }

      rawPackages.push(this.files[i]);
      manifestParameter.push([
        { id: 'name', type: 'string', name: 'manifest name', value: data.name },
        { id: 'tag', type: 'string', name: 'manifest tag', value: data.tag },
        { id: 'os', type: 'array', name: 'supported os', value: os },
      ]);
    }

    this.uploading = true;
    this.uploadEnabled = false;

    const allObservables = [];

    // upload
    const uploadedAndBuilt = this.uploadService.upload(
      this.softwareService.getSoftwareUploadRaw(this.repositoryName),
      rawPackages,
      manifestParameter,
      'file'
    );
    const uploaded = this.uploadService.upload(
      this.softwareService.getSoftwareUploadUrl(this.repositoryName),
      toUpload,
      [],
      'file'
    );

    // merge the uploads
    this.uploads = new Map([...uploadedAndBuilt, ...uploaded]);

    this.uploads.forEach((e) => {
      allObservables.push(e.progressObservable);
    });

    // Update state when we are finished
    forkJoin(allObservables).subscribe(
      (next) => {},
      (error) => {},
      () => {
        this.allFilesUploaded();
      }
    );
  }

  allFilesUploaded() {
    const oneFailed = Array.from(this.uploads.values()).some(
      (us) => us.state === UploadState.FAILED
    );
    if (oneFailed) {
      this.buttonText = 'Retry Upload';
      this.uploadFinished = false;
      this.dialogRef.disableClose = true;
    } else {
      this.buttonText = 'Close';
      this.uploadFinished = true;
      this.dialogRef.disableClose = false;
    }
    this.uploadEnabled = true;
    this.uploading = false;
  }

  hasState(file: File, state: UploadState) {
    const status = this.getUploadStatus(file);
    if (!status) {
      return false;
    }
    return status.state === state;
  }

  getUploadStatus(file: File): UploadStatus {
    if (this.uploads === undefined) {
      return null;
    }
    return this.uploads.get(file.name);
  }

  fileInProgress(file: File): boolean {
    if (!this.getUploadStatus(file)) {
      return false;
    }
    if (
      this.hasState(file, this.uploadState.FINISHED) ||
      this.hasState(file, this.uploadState.FAILED)
    ) {
      return false;
    }
    return true;
  }

  fileInQueue(file: File): boolean {
    if (this.hasState(file, this.uploadState.FINISHED)) {
      return false;
    }
    if (this.fileInProgress(file)) {
      return false;
    }
    if (this.hasState(file, this.uploadState.FAILED)) {
      return false;
    }
    return this.files.indexOf(file) !== -1;
  }

  getUploadProgress(file: File) {
    return this.getUploadStatus(file).progressObservable;
  }

  getResultDetails(file: File): string {
    const status = this.getUploadStatus(file);
    if (status === null) {
      return '';
    }
    if (this.hasState(file, UploadState.FAILED)) {
      return 'Upload failed. ' + status.detail;
    }
    if (this.hasState(file, UploadState.FINISHED)) {
      return this.resultDetailsEvaluation(status);
    }
  }

  private resultDetailsEvaluation(status: UploadStatus) {
    if (status.detail.length === 0) {
      return 'Software version already exists. Nothing to do.';
    }
    const softwares: ManifestKey[] = status.detail;
    return (
      'Upload successful. New software package(s): ' +
      softwares.map((key) => key.name + ' ' + key.tag).join(', ')
    );
  }

  removeFile(file: File) {
    const idx = this.files.indexOf(file);
    this.fileData.splice(idx, 1);
    this.files.splice(idx, 1);
    this.uploadEnabled = this.files.length > 0;
    this.dialogRef.disableClose = this.files.length > 0;
  }

  isAllInfoFilled(): boolean {
    for (const data of this.fileData) {
      if (!data.hive) {
        if (!data.name?.length || !data.tag?.length) {
          return false;
        }
        const os: OperatingSystem[] = [];
        for (const [key, value] of data.supportedOS.entries()) {
          if (value) {
            os.push(key);
          }
        }
        if (os.length === 0) {
          return false;
        }
      }
    }
    return true;
  }
}
