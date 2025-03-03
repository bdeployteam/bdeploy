import { Component, Input } from '@angular/core';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import {
  TableCellDisplay
} from '../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';
import { BdDataColumn } from '../../../../../../models/data';

@Component({
    selector: 'app-instance-banner-hint',
    templateUrl: './instance-banner-hint.component.html',
    styleUrls: ['./instance-banner-hint.component.css'],
    imports: [MatIcon, MatTooltip]
})
export class InstanceBannerHintComponent implements TableCellDisplay<InstanceDto> {
  @Input() record: InstanceDto;
  @Input() column: BdDataColumn<InstanceDto>;
}
