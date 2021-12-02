import { Component, Input, OnInit } from '@angular/core';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { FileListEntry } from '../data-files.component';

const MAX_FILE_SIZE = 1048576; // 1 MB

@Component({
  selector: 'app-data-edit',
  templateUrl: './data-edit.component.html',
  styleUrls: ['./data-edit.component.css'],
})
export class DataEditComponent implements OnInit {
  @Input() record: FileListEntry;

  constructor(public authService: AuthenticationService) {}

  ngOnInit(): void {}

  /* template */ isOversized() {
    return this.record?.entry?.size > MAX_FILE_SIZE;
  }
}
