import { Component, Input, OnInit } from '@angular/core';
import { ApplicationConfiguration, OperatingSystem } from 'src/app/models/gen.dtos';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { MatIcon } from '@angular/material/icon';
import { BdDataColumn } from '../../../../../models/data';
import {
  TableCellDisplay
} from '../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-process-name-and-os',
    templateUrl: './process-name-and-os.component.html',
    imports: [MatIcon]
})
export class ProcessNameAndOsComponent implements OnInit, TableCellDisplay<ApplicationConfiguration> {
  @Input() record: ApplicationConfiguration;
  @Input() column: BdDataColumn<ApplicationConfiguration>;

  protected appOs: OperatingSystem;

  ngOnInit(): void {
    this.appOs = getAppOs(this.record.application);
  }
}
