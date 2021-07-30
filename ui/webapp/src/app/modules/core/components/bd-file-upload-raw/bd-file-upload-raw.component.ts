import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ObjectChangeDetails, ObjectChangeType, OperatingSystem, UploadInfoDto } from 'src/app/models/gen.dtos';
import { ActivitiesService } from '../../services/activities.service';
import { ObjectChangesService } from '../../services/object-changes.service';
import { ImportState, ImportStatus, UploadService, UploadState, UploadStatus, UrlParameter } from '../../services/upload.service';

@Component({
  selector: 'app-bd-file-upload-raw',
  templateUrl: './bd-file-upload-raw.component.html',
  styleUrls: ['./bd-file-upload-raw.component.css'],
})
export class BdFileUploadRawComponent implements OnInit, OnDestroy {
  @Input() file: File;
  @Input() uploadUrl: string;
  @Input() importUrl: string;
  @Input() parameters: UrlParameter[];
  @Input() formDataParam = 'file';

  @Output() dismiss = new EventEmitter<File>();

  /* template */ uploadStatus: UploadStatus;
  /* template */ importStatus: ImportStatus;
  /* template */ processingHint$ = new BehaviorSubject<string>('Working on it...');

  /* template */ set supportAllOs(b: boolean) {
    const dto: UploadInfoDto = this.uploadStatus?.detail;
    if (!!dto) {
      if (b) {
        dto.supportedOperatingSystems = undefined;
      } else {
        dto.supportedOperatingSystems = [];
      }
    }
  }

  /* template */ get supportAllOs(): boolean {
    const dto: UploadInfoDto = this.uploadStatus?.detail;
    return !!dto && dto.supportedOperatingSystems === undefined;
  }

  /* template */ set supportWindows(b: boolean) {
    const dto: UploadInfoDto = this.uploadStatus?.detail;
    if (!!dto) {
      if (b) {
        if (dto.supportedOperatingSystems === undefined) {
          dto.supportedOperatingSystems = [OperatingSystem.WINDOWS];
        } else if (!dto.supportedOperatingSystems.includes(OperatingSystem.WINDOWS)) {
          dto.supportedOperatingSystems.push(OperatingSystem.WINDOWS);
        }
      } else {
        if (dto.supportedOperatingSystems !== undefined) {
          dto.supportedOperatingSystems = dto.supportedOperatingSystems.filter((e) => e !== OperatingSystem.WINDOWS);
        }
      }
    }
  }

  /* template */ get supportWindows(): boolean {
    const dto: UploadInfoDto = this.uploadStatus?.detail;
    return !!dto && !!dto.supportedOperatingSystems && dto.supportedOperatingSystems.includes(OperatingSystem.WINDOWS);
  }

  /* template */ set supportLinux(b: boolean) {
    const dto: UploadInfoDto = this.uploadStatus?.detail;
    if (!!dto) {
      if (b) {
        if (dto.supportedOperatingSystems === undefined) {
          dto.supportedOperatingSystems = [OperatingSystem.LINUX];
        } else if (!dto.supportedOperatingSystems.includes(OperatingSystem.LINUX)) {
          dto.supportedOperatingSystems.push(OperatingSystem.LINUX);
        }
      } else {
        if (dto.supportedOperatingSystems !== undefined) {
          dto.supportedOperatingSystems = dto.supportedOperatingSystems.filter((e) => e !== OperatingSystem.LINUX);
        }
      }
    }
  }

  /* template */ get supportLinux(): boolean {
    const dto: UploadInfoDto = this.uploadStatus?.detail;
    return !!dto && !!dto.supportedOperatingSystems && dto.supportedOperatingSystems.includes(OperatingSystem.LINUX);
  }

  private subscription: Subscription;

  constructor(private uploads: UploadService, private changes: ObjectChangesService, private activities: ActivitiesService) {}

  ngOnInit(): void {
    this.uploadStatus = this.uploads.uploadFile(this.uploadUrl, this.file, this.parameters, this.formDataParam);
    this.uploadStatus.stateObservable.pipe(finalize(() => this.uploadDone())).subscribe();

    this.subscription = this.changes.subscribe(ObjectChangeType.ACTIVITIES, { scope: [this.uploadStatus.scope] }, (e) => {
      this.onEventReceived(e.details[ObjectChangeDetails.ACTIVITIES]);
    });
  }

  isUserInputRequired(): boolean {
    return !!this.uploadStatus.detail && !(!!this.uploadStatus?.detail?.isHive || !!this.uploadStatus?.detail?.isProduct) && !!!this.importStatus;
  }

  isUserInputValid(): boolean {
    return (
      !!this.uploadStatus.detail &&
      this.uploadStatus?.detail?.name?.length > 0 &&
      this.uploadStatus?.detail?.tag?.length > 0 &&
      (this.uploadStatus?.detail?.supportedOperatingSystems === undefined || this.uploadStatus?.detail?.supportedOperatingSystems.length > 0)
    );
  }

  uploadDone() {
    this.uploadStatus.detail.filename = this.file.name;
    if (this.uploadStatus.detail.supportedOperatingSystems === null) {
      this.uploadStatus.detail.supportedOperatingSystems = []; // == unset
    }
    this.processingHint$.next(''); // clear last hint of upload
    if (!this.isUserInputRequired()) {
      this.import();
    }
  }

  import() {
    this.importStatus = this.uploads.importFile(this.importUrl, this.uploadStatus.detail);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private onEventReceived(e: string) {
    // we accept all events as we don't know the scope we're looking for beforehand.
    const events = this.activities.getActivitiesFromEvent(e, [this.uploadStatus.scope]);

    // each received event's root scope must match a scope of an UploadStatus object.
    // discard all events where this is not true.
    for (const event of events) {
      // if we do have a match, extract the most relevant message, set it, and then flag a table repaint.
      this.processingHint$.next(this.activities.getMostRelevantMessage(event));
    }
  }

  /* template */ getIcon() {
    if (this.isUploading() || this.isUploadProcessing()) {
      return 'cloud_upload';
    } else if (this.isUploadFinished()) {
      return 'cloud_done';
    } else if (this.isUploadFailed()) {
      return 'cloud_off';
    } else {
      return 'help';
    }
  }

  /* template */ getHeader() {
    if (this.isUploading() || this.isUploadProcessing()) {
      return `Uploading: ${this.file.name}`;
    } else if (this.isImporting()) {
      return `Importing: ${this.file.name}`;
    } else if (this.isUploadFinished()) {
      return `Success: ${this.file.name}`;
    } else if (this.isUploadFailed()) {
      return `Failed: ${this.file.name}`;
    } else {
      return 'Unknown State';
    }
  }

  /* template */ onDismiss() {
    if (this.isUploading()) {
      this.uploadStatus.cancel();
    }
    this.dismiss.emit(this.file);
  }

  /* template */ isUploadFinished() {
    return this.uploadStatus?.state === UploadState.FINISHED;
  }

  /* template */ isUploadFailed() {
    return this.uploadStatus?.state === UploadState.FAILED;
  }

  /* template */ isUploading() {
    return this.uploadStatus?.state === UploadState.UPLOADING;
  }

  /* template */ isUploadProcessing() {
    return this.uploadStatus?.state === UploadState.PROCESSING;
  }

  //

  /* template */ isImporting() {
    return this.importStatus?.state === ImportState.IMPORTING;
  }

  /* template */ isImportFinished() {
    return this.importStatus?.state === ImportState.FINISHED;
  }

  /* template */ isImportFailed() {
    return this.importStatus?.state === ImportState.FAILED;
  }
}
