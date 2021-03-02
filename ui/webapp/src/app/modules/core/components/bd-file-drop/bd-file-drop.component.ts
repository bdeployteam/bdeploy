import { Component, ElementRef, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Component({
  selector: 'app-bd-file-drop',
  templateUrl: './bd-file-drop.component.html',
  styleUrls: ['./bd-file-drop.component.css'],
})
export class BdFileDropComponent implements OnInit {
  /** Whether the control is disabled. */
  @Input() disabled = false; // TODO

  /** A list of allowed file name endings, like '.zip' */
  @Input() types: string[];

  /** Fired when a valid file is added */
  @Output() fileAdded = new EventEmitter<File>();

  @ViewChild('file', { static: true }) private fileRef: ElementRef;

  /* template */ active = false;
  /* template */ validationError$ = new BehaviorSubject<boolean>(false);

  constructor() {}

  ngOnInit(): void {}

  doSelectFiles() {
    this.fileRef.nativeElement.click();
  }

  onFilesAdded() {
    this.onFilesDropped(this.fileRef.nativeElement.files);
    this.fileRef.nativeElement.value = '';
  }

  onFilesDropped(files: FileList) {
    for (let i = 0; i < files.length; i++) {
      const file: File = files[i];
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
