import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { HiveInfoDto } from 'src/app/models/gen.dtos';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
    selector: 'app-pooling-status-cell',
    templateUrl: './pooling-status-cell.component.html',
    styleUrl: './pooling-status-cell.component.css',
    imports: [MatTooltip]
})
export class PoolingStatusCellComponent {
  @Input() record: HiveInfoDto;
  @Input() column: BdDataColumn<HiveInfoDto>;

  protected getState(): string {
    if ((this.record.pooling && !this.record.poolPath) || (!this.record.pooling && this.record.poolPath)) {
      return 'PENDING';
    } else if (this.record.pooling) {
      return 'ENABLED';
    } else {
      return 'DISABLED';
    }
  }
}
