import { Component, Input, inject } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceDto, InstanceOverallStatusDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { DatePipe } from '@angular/common';
import { CellComponent } from '../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

const STATUS_TIMEOUT = 1000 * 60 * 15;

@Component({
    selector: 'app-overall-status-column',
    templateUrl: './overall-status-column.component.html',
    styleUrls: ['./overall-status-column.component.css'],
    imports: [MatIcon, MatTooltip, DatePipe]
})
export class OverallStatusColumnComponent implements CellComponent<InstanceDto, InstanceOverallStatusDto>{
  private readonly cfg = inject(ConfigService);

  @Input() record: InstanceDto;
  @Input() column: BdDataColumn<InstanceDto, InstanceOverallStatusDto>;

  protected getStatus() {
    const s = this.column.data(this.record) as InstanceOverallStatusDto;
    if (s?.timestamp) {
      if (this.cfg.getCorrectedNow() - s.timestamp > STATUS_TIMEOUT) {
        return null;
      }
      return s;
    }
    return null;
  }
}
