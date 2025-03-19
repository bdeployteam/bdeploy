import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { InstanceDto, InstancePurpose } from 'src/app/models/gen.dtos';
import { NgClass } from '@angular/common';
import { MatTooltip } from '@angular/material/tooltip';
import { BdDataColumn } from '../../../../../../models/data';
import { CellComponent } from '../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-instance-purpose-short',
    templateUrl: './instance-purpose-short.component.html',
    styleUrls: ['./instance-purpose-short.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgClass, MatTooltip]
})
export class InstancePurposeShortComponent implements CellComponent<InstanceDto, InstancePurpose> {
  @Input() record: InstanceDto;
  @Input() column: BdDataColumn<InstanceDto, InstancePurpose>;
  
  protected getPurposeAbbrev() {
    return this.record.instanceConfiguration.purpose.charAt(0);
  }

  protected getPurposeClass(): string {
    return `local-${this.record.instanceConfiguration.purpose}`;
  }
}
