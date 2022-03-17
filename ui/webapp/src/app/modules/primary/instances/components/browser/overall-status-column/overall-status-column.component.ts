import { Component, Input } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { InstanceDto, InstanceOverallStatusDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-overall-status-column',
  templateUrl: './overall-status-column.component.html',
  styleUrls: ['./overall-status-column.component.css'],
})
export class OverallStatusColumnComponent {
  @Input() record: InstanceDto;
  @Input() column: BdDataColumn<InstanceDto>;

  /* template */ getStatus() {
    return this.column.data(this.record) as InstanceOverallStatusDto;
  }
}
