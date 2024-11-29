import { Component, Input, OnInit } from '@angular/core';
import { VariableType } from 'src/app/models/gen.dtos';
import { PortParam } from '../../../../services/ports-edit.service';

@Component({
    selector: 'app-port-type-cell',
    templateUrl: './port-type-cell.component.html',
    styleUrls: ['./port-type-cell.component.css'],
    standalone: false
})
export class PortTypeCellComponent implements OnInit {
  @Input() record: PortParam;

  protected shortType;
  protected shortDesc;

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
