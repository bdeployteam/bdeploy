import { Component, inject } from '@angular/core';
import { SoftwareUpdateService } from 'src/app/modules/primary/admin/services/software-update.service';

@Component({
    selector: 'app-software-upload',
    templateUrl: './software-upload.component.html',
    standalone: false
})
export class SoftwareUploadComponent {
  protected readonly software = inject(SoftwareUpdateService);

  protected files: File[] = [];

  protected fileAdded(file: File) {
    this.files.push(file);
  }

  protected onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
