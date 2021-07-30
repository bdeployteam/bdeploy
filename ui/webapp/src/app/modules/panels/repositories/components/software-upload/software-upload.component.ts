import { Component, OnInit } from '@angular/core';
import { RepositoryService } from 'src/app/modules/primary/repositories/services/repository.service';

@Component({
  selector: 'app-software-upload',
  templateUrl: './software-upload.component.html',
  styleUrls: ['./software-upload.component.css'],
})
export class SoftwareUploadComponent implements OnInit {
  /* template */ files: File[] = [];

  constructor(public repositoryService: RepositoryService) {}

  ngOnInit(): void {}

  fileAdded(file: File) {
    this.files.push(file);
  }

  onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
