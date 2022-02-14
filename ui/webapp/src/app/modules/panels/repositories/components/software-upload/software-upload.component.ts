import { Component } from '@angular/core';
import { RepositoryService } from 'src/app/modules/primary/repositories/services/repository.service';

@Component({
  selector: 'app-software-upload',
  templateUrl: './software-upload.component.html',
})
export class SoftwareUploadComponent {
  /* template */ files: File[] = [];

  constructor(public repositoryService: RepositoryService) {}

  /* template */ fileAdded(file: File) {
    this.files.push(file);
  }

  /* template */ onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
