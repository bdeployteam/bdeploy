import { Component } from '@angular/core';
import { ManifestKey } from 'src/app/models/gen.dtos';
import {
  UploadStatus,
  UrlParameter,
} from 'src/app/modules/core/services/upload.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';

interface ImportFile {
  file: File;
  parameters: UrlParameter[];
}

@Component({
  selector: 'app-import-instance',
  templateUrl: './import-instance.component.html',
})
export class ImportInstanceComponent {
  /* template */ files: ImportFile[] = [];

  /* template */ resultEval = (s: UploadStatus) => {
    if (s.detail.length === 0) {
      return 'Nothing to do.';
    }
    const tags: ManifestKey[] = s.detail;
    return 'Created Instance Version: ' + tags.map((key) => key.tag).join(',');
  };

  constructor(public instances: InstancesService) {}

  /* template */ fileAdded(file: File) {
    this.files.push({ file: file, parameters: [] });
  }

  /* template */ onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
