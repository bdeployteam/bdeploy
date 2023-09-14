import { Component, inject } from '@angular/core';
import { RepositoryService } from 'src/app/modules/primary/repositories/services/repository.service';

@Component({
  selector: 'app-software-upload',
  templateUrl: './software-upload.component.html',
})
export class SoftwareUploadComponent {
  protected repositoryService = inject(RepositoryService);

  protected files: File[] = [];

  protected fileAdded(file: File) {
    this.files.push(file);
  }

  protected onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
