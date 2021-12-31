import { Component, Input, OnInit } from '@angular/core';
import { FileStatusType } from 'src/app/models/gen.dtos';
import { ConfigFile, ConfigFilesService } from '../../../../services/config-files.service';

@Component({
  selector: 'app-edit-action',
  templateUrl: './edit-action.component.html',
  styleUrls: ['./edit-action.component.css'],
})
export class EditActionComponent implements OnInit {
  @Input() record: ConfigFile;

  /* template */ isEditAllowed: boolean;
  /* template */ path: string;
  /* template */ isText: boolean;

  constructor(private cfgFiles: ConfigFilesService) {}

  ngOnInit(): void {
    this.isEditAllowed = this.canEdit();
    this.isText = this.cfgFiles.isText(this.record);
    this.path = this.cfgFiles.getPath(this.record);
  }

  private canEdit(): boolean {
    if (!this.cfgFiles.isText(this.record)) {
      return false; // freshly added (not persistent yet) or not text file.
    }
    if (this.record.modification?.type === FileStatusType.DELETE) {
      return false;
    }

    return true;
  }
}
