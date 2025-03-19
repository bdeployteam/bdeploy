import { Component, Input, OnInit } from '@angular/core';
import { VariableType } from 'src/app/models/gen.dtos';
import { PortParam } from '../../../../services/ports-edit.service';
import { NgClass } from '@angular/common';
import { MatTooltip } from '@angular/material/tooltip';
import { BdDataColumn } from '../../../../../../../models/data';
import {
  CellComponent
} from '../../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-port-type-cell',
    templateUrl: './port-type-cell.component.html',
    styleUrls: ['./port-type-cell.component.css'],
    imports: [NgClass, MatTooltip]
})
export class PortTypeCellComponent implements OnInit, CellComponent<PortParam, string> {
  @Input() record: PortParam;
  @Input() column: BdDataColumn<PortParam, string>;

  protected shortType: string;
  protected shortDesc: string;

  ngOnInit() {
    if (this.record) {
      switch (this.record.type) {
        case VariableType.SERVER_PORT:
          this.shortType = 'S';
          this.shortDesc = 'Server Port';
          break;
        case VariableType.CLIENT_PORT:
          this.shortType = 'C';
          this.shortDesc = 'Client Port';
          break;
        case VariableType.URL:
          this.shortType = 'U';
          this.shortDesc = 'Port embedded in URL';
          break;
      }
    }
  }
}
