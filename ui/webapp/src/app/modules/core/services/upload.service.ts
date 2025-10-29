import {
  HttpClient,
  HttpErrorResponse,
  HttpEventType,
  HttpHeaders,
  HttpParams,
  HttpRequest,
  HttpResponse
} from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { UploadInfoDto } from 'src/app/models/gen.dtos';
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
export class UploadStatus<T> {
  file: File;

  /** The upload progress in percent (0-100)  */
  progressObservable: Observable<number>;

  /** Current state */
  state: UploadState;

  /** Notification when the state changes */
  stateObservable: Observable<UploadState>;

  /** The response body if OK */
  detail?: T;

  /** The error if the request failed */
  error?: string;

  /** Activity scope ID */
  scope: string;

  /**
   * Progress Hint
   * @deprecated no longer used in new UI
   */
  processingHint: string;

  /** Cancels the ongoing request */
  cancel: () => void;
}

export interface UrlParameter {
  id: string;
  name: string;
  type: string;
  value: string | number | boolean;
}

export enum ImportState {
  /** Import in progress */
  IMPORTING,

  /** Import finished. No errors reported  */
  FINISHED,

  /** Import failed. */
  FAILED,
}

export class ImportStatus {
  filename: string;

  /** Current state */
  state: ImportState;

  /** Notification when the state changes */
  stateObservable: Observable<ImportState>;

  /** The error message if failed */
  detail: unknown;
}

@Injectable({
  providedIn: 'root',
})
export class UploadService {
  private readonly http = inject(HttpClient);

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
  public upload<T>(
    url: string,
    files: File[],
    urlParameter: UrlParameter[][],
    formDataParam: string,
  ): Map<string, UploadStatus<T>> {
    const result: Map<string, UploadStatus<T>> = new Map<string, UploadStatus<T>>();

    for (let i = 0; i < files.length; ++i) {
      const file = files[i];
      const params = urlParameter[i];

      result.set(file.name, this.uploadFile(url, file, params, formDataParam));
    }
    return result;
  }

  /**
   * Uploads the given file to the given URL and returns an observable result to track the upload status.
   *
   *  @param url the target URL to post the files to
   *  @param file the files to upload
   *  @param urlParameters additional url parameter per file
   *  @param formDataParam the FormData's property name that holds the file
   *  @returns a map containing the upload status for each file
   */
  public uploadFile<T>(url: string, file: File, urlParameters: UrlParameter[], formDataParam: string): UploadStatus<T> {
    // create a new progress-subject for every file
    const uploadStatus = new UploadStatus<T>();
    const progressSubject = new Subject<number>();
    const stateSubject = new BehaviorSubject<UploadState>(UploadState.UPLOADING);
    uploadStatus.file = file;
    uploadStatus.progressObservable = progressSubject.asObservable();
    uploadStatus.stateObservable = stateSubject.asObservable();
    uploadStatus.stateObservable.subscribe((state) => {
      uploadStatus.state = state;
    });
    uploadStatus.scope = this.uuidv4();

    // create a new multipart-form for every file
    const formData: FormData = new FormData();
    formData.append(formDataParam, file, file.name);

    // Suppress global error handling and enable progress reporting
    const options: Record<string, unknown> = {
      reportProgress: true,
      headers: suppressGlobalErrorHandling(
        // TODO: this is no longer supported.
        new HttpHeaders({ 'X-Proxy-Activity-Scope': uploadStatus.scope }),
      )
    };

    // create and set additional HttpParams
    if (urlParameters) {
      let httpParams = new HttpParams();
      urlParameters.forEach((p) => {
        if (p.type === 'boolean') {
          httpParams = httpParams.set(p.id, p.value === true ? 'true' : 'false');
        } else {
          httpParams = httpParams.set(p.id, p.value);
        }
      });
      options['params'] = httpParams;
    }

    // create a http-post request and pass the form
    const req = new HttpRequest('POST', url, formData, options);
    const sub = this.http.request<T>(req).subscribe({
      next: (event) => {
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
      error: (error: HttpErrorResponse) => {
        uploadStatus.error = `${error.statusText} (Status ${error.status})`;
        stateSubject.next(UploadState.FAILED);
        progressSubject.complete();
        stateSubject.complete();
      },
    });
    uploadStatus.cancel = () => sub.unsubscribe();
    return uploadStatus;
  }

  public importFile(url: string, dto: UploadInfoDto): ImportStatus {
    const importStatus = new ImportStatus();
    const stateSubject = new BehaviorSubject<ImportState>(ImportState.IMPORTING);
    importStatus.filename = dto.filename;
    importStatus.stateObservable = stateSubject.asObservable();
    importStatus.stateObservable.subscribe((state) => {
      importStatus.state = state;
    });

    this.http
      .post<UploadInfoDto>(url, dto, {
        headers: suppressGlobalErrorHandling(new HttpHeaders()),
      })
      .subscribe({
        next: (d: UploadInfoDto) => {
          importStatus.detail = d.details;
          stateSubject.next(ImportState.FINISHED);
          stateSubject.complete();
        },
        error: (error: HttpErrorResponse) => {
          importStatus.detail = `${error.statusText} (Status ${error.status})`;
          stateSubject.next(ImportState.FAILED);
          stateSubject.complete();
        },
      });
    return importStatus;
  }

  private uuidv4() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
      const r = (Math.random() * 16) | 0,
        v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }
}
