import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ManifestKey } from 'src/app/models/gen.dtos';
import { UploadService, UploadState, UploadStatus, UrlParameter } from '../../services/upload.service';

@Component({
  selector: 'app-bd-file-upload',
  templateUrl: './bd-file-upload.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdFileUploadComponent implements OnInit {
  @Input() file: File;
  @Input() url: string;
  @Input() parameters: UrlParameter[];
  @Input() formDataParam = 'file';
  @Input() resultEvaluator: (status: UploadStatus) => string = this.defaultResultDetailsEvaluation;

  // eslint-disable-next-line @angular-eslint/no-output-native
  @Output() success = new EventEmitter<any>();
  @Output() dismiss = new EventEmitter<File>();

  private uploads = inject(UploadService);

  /* template */ status: UploadStatus;
  /* template */ finished$ = new BehaviorSubject<boolean>(false);
  /* template */ failed$ = new BehaviorSubject<boolean>(false);
  /* template */ uploading$ = new BehaviorSubject<boolean>(false);
  /* template */ processing$ = new BehaviorSubject<boolean>(false);
  /* template */ icon$ = new BehaviorSubject<string>(this.getIcon());
  /* template */ header$ = new BehaviorSubject<string>(this.getHeader());

  ngOnInit(): void {
    this.status = this.uploads.uploadFile(this.url, this.file, this.parameters, this.formDataParam);

    this.status.stateObservable.subscribe((state) => {
      this.setProcessDetails();
      if (state === UploadState.FINISHED) {
        this.success.emit(true);
      }
    });

    this.status.progressObservable.subscribe(() => {
      this.setProcessDetails();
    });
  }

  private setProcessDetails() {
    this.finished$.next(this.status.state === UploadState.FINISHED);
    this.failed$.next(this.status.state === UploadState.FAILED);
    this.uploading$.next(this.status.state === UploadState.UPLOADING);
    this.processing$.next(this.status.state === UploadState.PROCESSING);
    this.icon$.next(this.getIcon());
    this.header$.next(this.getHeader());
  }

  private defaultResultDetailsEvaluation(status: UploadStatus) {
    if (status.detail.length === 0) {
      return 'Software version already exists. Nothing to do.';
    }
    const softwares: ManifestKey[] = status.detail;
    return 'New software: ' + softwares.map((key) => key.name + ' ' + key.tag).join(',');
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
