import { Directive, EventEmitter, HostListener, Output } from '@angular/core';

/**
 * The purpose of this directive is to receive the raw files as a JavaScript FileList.
 * Then emit them to the parent component. It will also emit a custom event when files are hovered on the drop zone area.
 */
@Directive({
    selector: '[appFileDrop]',
    standalone: false
})
export class FileDropDirective {
  @Output() filesDropped = new EventEmitter<FileList>();
  @Output() filesHovered = new EventEmitter<boolean>();

  @HostListener('drop', ['$event'])
  onDrop($event) {
    $event.preventDefault();

    const transfer = $event.dataTransfer;
    this.filesDropped.emit(transfer.files);
    this.filesHovered.emit(false);
  }

  @HostListener('dragover', ['$event'])
  onDragOver($event) {
    $event.preventDefault();
    this.filesHovered.emit(true);
  }

  @HostListener('dragleave', ['$event'])
  onDragLeave() {
    this.filesHovered.emit(false);
  }
}
