import { Component } from '@angular/core';
import { SoftwareUpdateService } from 'src/app/modules/primary/admin/services/software-update.service';

@Component({
  selector: 'app-software-upload',
  templateUrl: './software-upload.component.html',
})
export class SoftwareUploadComponent {
  /* template */ files: File[] = [];

  constructor(public software: SoftwareUpdateService) {}

  /* template */ fileAdded(file: File) {
    this.files.push(file);
  }

  /* template */ onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
