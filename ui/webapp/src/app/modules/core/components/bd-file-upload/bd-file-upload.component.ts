import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { ManifestKey, ObjectChangeDetails, ObjectChangeType } from 'src/app/models/gen.dtos';
import { ActivitiesService } from '../../services/activities.service';
import { ObjectChangesService } from '../../services/object-changes.service';
import { UploadService, UploadState, UploadStatus, UrlParameter } from '../../services/upload.service';

@Component({
  selector: 'app-bd-file-upload',
  templateUrl: './bd-file-upload.component.html',
  styleUrls: ['./bd-file-upload.component.css'],
})
export class BdFileUploadComponent implements OnInit, OnDestroy {
  @Input() file: File;
  @Input() url: string;
  @Input() parameters: UrlParameter[];
  @Input() formDataParam = 'file';
  @Input() resultEvaluator: (status: UploadStatus) => string = this.defaultResultDetailsEvaluation;

  @Output() dismiss = new EventEmitter<File>();

  /* template */ status: UploadStatus;
  /* template */ processingHint$ = new BehaviorSubject<string>('Working on it...');

  private subscription: Subscription;

  constructor(
    private uploads: UploadService,
    private changes: ObjectChangesService,
    private activities: ActivitiesService
  ) {}

  ngOnInit(): void {
    this.status = this.uploads.uploadFile(this.url, this.file, this.parameters, this.formDataParam);

    this.subscription = this.changes.subscribe(ObjectChangeType.ACTIVITIES, { scope: [this.status.scope] }, (e) => {
      this.onEventReceived(e.details[ObjectChangeDetails.ACTIVITIES]);
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private defaultResultDetailsEvaluation(status: UploadStatus) {
    if (status.detail.length === 0) {
      return 'Software version already exists. Nothing to do.';
    }
    const softwares: ManifestKey[] = status.detail;
    return 'New software: ' + softwares.map((key) => key.name + ' ' + key.tag).join(',');
  }

  private onEventReceived(e: string) {
    // we accept all events as we don't know the scope we're looking for beforehand.
    const events = this.activities.getActivitiesFromEvent(e, [this.status.scope]);

    // each received event's root scope must match a scope of an UploadStatus object.
    // discard all events where this is not true.
    for (const event of events) {
      // if we do have a match, extract the most relevant message, set it, and then flag a table repaint.
      this.processingHint$.next(this.activities.getMostRelevantMessage(event));
    }
  }

  /* template */ getIcon() {
    if (this.isUploading() || this.isProcessing()) {
      return 'cloud_upload';
    } else if (this.isFinished()) {
      return 'cloud_done';
    } else if (this.isFailed()) {
      return 'cloud_off';
    } else {
      return 'help';
    }
  }

  /* template */ getHeader() {
    if (this.isUploading() || this.isProcessing()) {
      return `Uploading: ${this.file.name}`;
    } else if (this.isFinished()) {
      return `Success: ${this.file.name}`;
    } else if (this.isFailed()) {
      return `Failed: ${this.file.name}`;
    } else {
      return 'Unknown State';
    }
  }

  /* template */ onDismiss() {
    if (this.isUploading()) {
      this.status.cancel();
    }
    this.dismiss.emit(this.file);
  }

  /* template */ isFinished() {
    return this.status.state === UploadState.FINISHED;
  }

  /* template */ isFailed() {
    return this.status.state === UploadState.FAILED;
  }

  /* template */ isUploading() {
    return this.status.state === UploadState.UPLOADING;
  }

  /* template */ isProcessing() {
    return this.status.state === UploadState.PROCESSING;
  }
}
