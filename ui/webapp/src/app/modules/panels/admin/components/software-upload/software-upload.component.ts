import { Component, OnInit } from '@angular/core';
import { SoftwareUpdateService } from 'src/app/modules/primary/admin/services/software-update.service';

@Component({
  selector: 'app-software-upload',
  templateUrl: './software-upload.component.html',
  styleUrls: ['./software-upload.component.css'],
})
export class SoftwareUploadComponent implements OnInit {
  /* template */ files: File[] = [];

  constructor(public software: SoftwareUpdateService) {}

  ngOnInit(): void {}

  /* template */ fileAdded(file: File) {
    this.files.push(file);
  }

  /* template */ onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
