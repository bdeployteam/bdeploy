import { Component, Input, OnInit } from '@angular/core';
import { ParameterType } from 'src/app/models/gen.dtos';
import { PortParam } from '../../../../services/ports-edit.service';

@Component({
  selector: 'app-port-type-cell',
  templateUrl: './port-type-cell.component.html',
  styleUrls: ['./port-type-cell.component.css'],
})
export class PortTypeCellComponent implements OnInit {
  @Input() record: PortParam;

  /* template */ shortType;
  /* template */ shortDesc;

  ngOnInit() {
    if (this.record) {
      switch (this.record.type) {
        case ParameterType.SERVER_PORT:
          this.shortType = 'S';
          this.shortDesc = 'Server Port';
          break;
        case ParameterType.CLIENT_PORT:
          this.shortType = 'C';
          this.shortDesc = 'Client Port';
          break;
        case ParameterType.URL:
          this.shortType = 'U';
          this.shortDesc = 'Port embedded in URL';
          break;
      }
    }
  }
}
