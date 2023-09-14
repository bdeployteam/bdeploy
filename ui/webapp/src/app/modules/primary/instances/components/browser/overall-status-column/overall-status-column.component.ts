import { Component, Input, inject } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceDto, InstanceOverallStatusDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';

const STATUS_TIMEOUT = 1000 * 60 * 15;

@Component({
  selector: 'app-overall-status-column',
  templateUrl: './overall-status-column.component.html',
  styleUrls: ['./overall-status-column.component.css'],
})
export class OverallStatusColumnComponent {
  private cfg = inject(ConfigService);

  @Input() record: InstanceDto;
  @Input() column: BdDataColumn<InstanceDto>;

  protected getStatus() {
    const s = this.column.data(this.record) as InstanceOverallStatusDto;
    if (s && s.timestamp) {
      if (this.cfg.getCorrectedNow() - s.timestamp > STATUS_TIMEOUT) {
        return null;
      }
      return s;
    }
    return null;
  }
}
