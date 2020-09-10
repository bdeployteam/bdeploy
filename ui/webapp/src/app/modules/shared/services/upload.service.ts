import {
  HttpClient,
  HttpEventType,
  HttpHeaders,
  HttpParams,
  HttpRequest,
  HttpResponse
} from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { Logger, LoggingService } from '../../core/services/logging.service';
import { suppressGlobalErrorHandling } from '../utils/server.utils';

/** Enumeration of the possible states of an upload */
export enum UploadState {
  /** Files are transferred to the server */
  UPLOADING,

  /** Server side processing in progress */
  PROCESSING,

  /** Upload finished. No errors reported  */
  FINISHED,

  /** Upload failed. */
  FAILED,
}

/** Status of each file upload */
export class UploadStatus {
  file: File;

  /** The upload progress in percent (0-100)  */
  progressObservable: Observable<number>;

  /** Current state */
  state: UploadState;

  /** Notification when the state changes */
  stateObservable: Observable<UploadState>;

  /** The error message if failed. Or the response body if OK */
  detail: any;

  /** Activity scope ID */
  scope: string;

  /** Progress Hint */
  processingHint: string;
}

export interface UrlParameter {
  id: string;
  name: string;
  type: string;
  value: any;
}

@Injectable({
  providedIn: 'root',
})
export class UploadService {
  private readonly log: Logger = this.loggingService.getLogger('UploadService');

  constructor(
    private http: HttpClient,
    private loggingService: LoggingService
  ) {}

  /**
   * Uploads the given files to the given URL and returns an observable result to track the upload status. For
   * each file a separate HTTP-POST request will be created.
   *
   *  @param url the target URL to post the files to
   *  @param files the files to upload
   *  @param urlParameter additional url parameter per file
   *  @param formDataParam the FormData's property name that holds the file
   *  @returns a map containing the upload status for each file
   */
  public upload(
    url: string,
    files: File[],
    urlParameter: UrlParameter[][],
    formDataParam: string
  ): Map<string, UploadStatus> {
    const result: Map<string, UploadStatus> = new Map();

    for (let i = 0; i < files.length; ++i) {
      const file = files[i];
      const params = urlParameter[i];

      // create a new progress-subject for every file
      const uploadStatus = new UploadStatus();
      const progressSubject = new Subject<number>();
      const stateSubject = new Subject<UploadState>();
      uploadStatus.file = file;
      uploadStatus.progressObservable = progressSubject.asObservable();
      uploadStatus.stateObservable = stateSubject.asObservable();
      uploadStatus.stateObservable.subscribe((state) => {
        uploadStatus.state = state;
      });
      uploadStatus.scope = this.uuidv4();
      result.set(file.name, uploadStatus);
      stateSubject.next(UploadState.UPLOADING);

      // create a new multipart-form for every file
      const formData: FormData = new FormData();
      formData.append(formDataParam, file, file.name);

      // Suppress global error handling and enable progress reporting
      const options = {
        reportProgress: true,
        headers: suppressGlobalErrorHandling(
          new HttpHeaders({ 'X-Proxy-Activity-Scope': uploadStatus.scope })
        ),
      };

      // create and set additional HttpParams
      if (params) {
        let httpParams = new HttpParams();
        params.forEach((p) => {
          if (p.type === 'boolean') {
            httpParams = httpParams.set(
              p.id,
              p.value === true ? 'true' : 'false'
            );
          } else {
            httpParams = httpParams.set(p.id, p.value);
          }
        });
        options['params'] = httpParams;
      }

      // create a http-post request and pass the form
      const req = new HttpRequest('POST', url, formData, options);
      this.http.request(req).subscribe(
        (event) => {
          if (event.type === HttpEventType.UploadProgress) {
            const percentDone = Math.round((100 * event.loaded) / event.total);
            progressSubject.next(percentDone);

            // Notify that upload is done and that server-side processing begins
            if (percentDone === 100) {
              progressSubject.complete();
              stateSubject.next(UploadState.PROCESSING);
            }
          } else if (event instanceof HttpResponse) {
            uploadStatus.detail = event.body;
            stateSubject.next(UploadState.FINISHED);
            stateSubject.complete();
          }
        },
        (error) => {
          uploadStatus.detail =
            error.statusText + ' (Status ' + error.status + ')';
          stateSubject.next(UploadState.FAILED);
          progressSubject.complete();
          stateSubject.complete();
        }
      );
    }
    return result;
  }

  uuidv4() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (
      c
    ) {
      // tslint:disable-next-line:no-bitwise
      const r = (Math.random() * 16) | 0, v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }
}
