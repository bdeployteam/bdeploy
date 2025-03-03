import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { NgClass } from '@angular/common';
import { MatTooltip } from '@angular/material/tooltip';
import {
  TableCellDisplay
} from '../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';
import { BdDataColumn } from '../../../../../../models/data';

@Component({
    selector: 'app-instance-purpose-short',
    templateUrl: './instance-purpose-short.component.html',
    styleUrls: ['./instance-purpose-short.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgClass, MatTooltip]
})
export class InstancePurposeShortComponent implements TableCellDisplay<InstanceDto> {
  @Input() record: InstanceDto;
  @Input() column: BdDataColumn<InstanceDto>;
  
  protected getPurposeAbbrev() {
    return this.record.instanceConfiguration.purpose.charAt(0);
  }

  protected getPurposeClass(): string {
    return `local-${this.record.instanceConfiguration.purpose}`;
  }
}
