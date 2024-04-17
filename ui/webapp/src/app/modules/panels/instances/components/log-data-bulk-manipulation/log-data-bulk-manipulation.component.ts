import { Component, inject } from '@angular/core';
import { DataFilesBulkService } from 'src/app/modules/primary/instances/services/data-files-bulk.service';

@Component({
  selector: 'app-log-data-bulk-manipulation',
  templateUrl: './log-data-bulk-manipulation.component.html',
})
export class LogDataBulkManipulationComponent {
  protected dataFilesBulkService = inject(DataFilesBulkService);

  protected onDownload() {
    this.dataFilesBulkService.downloadDataFiles(this.dataFilesBulkService.selectedDataFiles);
  }
}
