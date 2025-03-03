import { ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { FileDropDirective } from '../../directives/file-drop.directive';
import { NgClass, AsyncPipe } from '@angular/common';
import { MatIcon } from '@angular/material/icon';

@Component({
    selector: 'app-bd-file-drop',
    templateUrl: './bd-file-drop.component.html',
    styleUrls: ['./bd-file-drop.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FileDropDirective, NgClass, MatIcon, AsyncPipe]
})
export class BdFileDropComponent {
  /** Whether the control is disabled. */
  @Input() disabled = false;

  /** A list of allowed file name endings, like '.zip' */
  @Input() types: string[];

  /** The hint text about the upload field. */
  @Input() hintText = 'Drop files to upload';

  /** Fired when a valid file is added */
  @Output() fileAdded = new EventEmitter<File>();

  @ViewChild('file', { static: true }) private readonly fileRef: ElementRef;

  protected active = false;
  protected validationError$ = new BehaviorSubject<boolean>(false);

  doSelectFiles() {
    this.fileRef.nativeElement.click();
  }

  onFilesAdded() {
    this.onFilesDropped(this.fileRef.nativeElement.files);
    this.fileRef.nativeElement.value = '';
  }

  onFilesDropped(files: FileList) {
    if (this.disabled) {
      return;
    }
    for (const file of files) {
      if (this.nameVerifier(file.name)) {
        this.fileAdded.emit(file);
      } else {
        this.validationError$.next(true);
        setTimeout(() => this.validationError$.next(false), 2000);
      }
    }
  }

  private nameVerifier(name: string) {
    if (!this.types?.length) {
      return true;
    }

    for (const ext of this.types) {
      if (name.toLowerCase().endsWith(ext.toLowerCase())) {
        return true;
      }
    }
    return false; // extensions given, but none matched.
  }
}
