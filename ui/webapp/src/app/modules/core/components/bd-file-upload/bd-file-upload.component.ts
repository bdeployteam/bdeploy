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

  @Output() success = new EventEmitter<any>();
  @Output() dismiss = new EventEmitter<File>();

  /* template */ status: UploadStatus;
  /* template */ processingHint$ = new BehaviorSubject<string>('Working on it...');
  /* template */ finished$ = new BehaviorSubject<boolean>(false);
  /* template */ failed$ = new BehaviorSubject<boolean>(false);
  /* template */ uploading$ = new BehaviorSubject<boolean>(false);
  /* template */ processing$ = new BehaviorSubject<boolean>(false);
  /* template */ icon$ = new BehaviorSubject<string>(this.getIcon());
  /* template */ header$ = new BehaviorSubject<string>(this.getHeader());

  private subscription: Subscription;

  constructor(private uploads: UploadService, private changes: ObjectChangesService, private activities: ActivitiesService) {}

  ngOnInit(): void {
    this.status = this.uploads.uploadFile(this.url, this.file, this.parameters, this.formDataParam);
    this.subscription = this.changes.subscribe(ObjectChangeType.ACTIVITIES, { scope: [this.status.scope] }, (e) => {
      this.onEventReceived(e.details[ObjectChangeDetails.ACTIVITIES]);
    });

    this.subscription.add(
      this.status.stateObservable.subscribe((state) => {
        this.setProcessDetails();
        if (state === UploadState.FINISHED) {
          this.success.emit(true);
        }
      })
    );
    this.subscription.add(
      this.status.progressObservable.subscribe(() => {
        this.setProcessDetails();
      })
    );
  }

  private setProcessDetails() {
    this.finished$.next(this.status.state === UploadState.FINISHED);
    this.failed$.next(this.status.state === UploadState.FAILED);
    this.uploading$.next(this.status.state === UploadState.UPLOADING);
    this.processing$.next(this.status.state === UploadState.PROCESSING);
    this.icon$.next(this.getIcon());
    this.header$.next(this.getHeader());
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

  private getIcon() {
    if (this.uploading$.value || this.processing$.value) {
      return 'cloud_upload';
    } else if (this.finished$.value) {
      return 'cloud_done';
    } else if (this.failed$.value) {
      return 'cloud_off';
    } else {
      return 'help';
    }
  }

  private getHeader() {
    if (this.uploading$.value || this.processing$.value) {
      return `Uploading: ${this.file.name}`;
    } else if (this.finished$.value) {
      return `Success: ${this.file.name}`;
    } else if (this.failed$.value) {
      return `Failed: ${this.file.name}`;
    } else {
      return 'Unknown State';
    }
  }

  /* template */ onDismiss() {
    if (this.uploading$.value) {
      this.status.cancel();
    }
    this.dismiss.emit(this.file);
  }
}
