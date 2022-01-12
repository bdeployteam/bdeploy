import { Component, forwardRef, Inject, Input, OnInit } from '@angular/core';
import { ConfigFile, ConfigFilesService } from '../../../../services/config-files.service';
import { ConfigFilesComponent } from '../config-files.component';

@Component({
  selector: 'app-delete-action',
  templateUrl: './delete-action.component.html',
  styleUrls: ['./delete-action.component.css'],
})
export class DeleteActionComponent implements OnInit {
  @Input() record: ConfigFile;

  constructor(private cfgFiles: ConfigFilesService, @Inject(forwardRef(() => ConfigFilesComponent)) private parent: ConfigFilesComponent) {}

  ngOnInit(): void {}

  /* template */ onDelete(): void {
    const name = this.cfgFiles.getPath(this.record);
    this.parent.dialog.confirm(`Delete ${name}?`, `This will remove the file ${name} from the current set of configuration files.`).subscribe((r) => {
      if (r) {
        this.cfgFiles.delete(name);
      }
    });
  }
}
