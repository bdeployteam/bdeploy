import { Component, OnInit } from '@angular/core';
import { ManifestKey } from 'src/app/models/gen.dtos';
import { UploadStatus } from 'src/app/modules/core/services/upload.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';

@Component({
  selector: 'app-import-instance',
  templateUrl: './import-instance.component.html',
  styleUrls: ['./import-instance.component.css'],
})
export class ImportInstanceComponent implements OnInit {
  /* template */ files: File[] = [];

  /* template */ resultEval = (s: UploadStatus) => {
    if (s.detail.length === 0) {
      return 'Nothing to do.';
    }
    const tags: ManifestKey[] = s.detail;
    return 'Created Instance Version: ' + tags.map((key) => key.tag).join(',');
  };

  constructor(public instances: InstancesService) {}

  ngOnInit(): void {}

  fileAdded(file: File) {
    this.files.push(file);
  }

  onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
