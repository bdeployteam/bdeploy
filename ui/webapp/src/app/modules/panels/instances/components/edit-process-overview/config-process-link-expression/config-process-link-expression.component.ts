import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { BdDataColumn, BdDataGrouping } from 'src/app/models/data';
import {
  ApplicationConfiguration,
  InstanceConfigurationDto,
  SystemConfiguration,
} from 'src/app/models/gen.dtos';
import { BdPopupDirective } from 'src/app/modules/core/components/bd-popup/bd-popup.directive';
import {
  gatherPathExpansions,
  gatherProcessExpansions,
  gatherSpecialExpansions,
  gatherVariableExpansions,
  LinkVariable,
} from 'src/app/modules/core/utils/linked-values.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';

const colVarName: BdDataColumn<LinkVariable> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  description: 'Name of the variable',
  width: '170px',
};

const colVarValue: BdDataColumn<LinkVariable> = {
  id: 'preview',
  name: 'Preview',
  data: (r) => r.preview,
  description: 'Preview value of the variable',
  width: '150px',
};

const colVarDesc: BdDataColumn<LinkVariable> = {
  id: 'desc',
  name: 'Description',
  data: (r) => r.description,
  description: 'Detailed description of the variable',
};

@Component({
  selector: 'app-config-process-link-expression',
  templateUrl: './config-process-link-expression.component.html',
})
export class ConfigProcessLinkExpressionComponent implements OnInit {
  @Input() process: ApplicationConfiguration;
  @Input() instance: InstanceConfigurationDto;
  @Input() system: SystemConfiguration;
  @Input() popup: BdPopupDirective;

  @Output() linkSelected = new EventEmitter<string>();

  /* template */ varRecords: LinkVariable[];
  /* temlpate */ paramRecords: LinkVariable[];
  /* template */ pathRecords: LinkVariable[];
  /* template */ specialRecords: LinkVariable[];

  /* template */ varColumns: BdDataColumn<LinkVariable>[] = [
    colVarName,
    colVarValue,
    colVarDesc,
  ];
  /* temlpate */ paramGrouping: BdDataGrouping<LinkVariable>[] = [
    {
      definition: { group: (r) => r.group, name: 'Application' },
      selected: [],
    },
  ];

  constructor(private edit: InstanceEditService) {}

  ngOnInit(): void {
    // instance variables take precedence over system variables
    this.varRecords = gatherVariableExpansions(this.instance, this.system);
    this.paramRecords = gatherProcessExpansions(
      this.instance,
      this.process,
      this.edit.stateApplications$.value
    );
    this.pathRecords = gatherPathExpansions();
    this.specialRecords = gatherSpecialExpansions(this.instance, this.process);
  }

  /* template */ onSelect(v: LinkVariable) {
    this.linkSelected.emit(v.link);
    this.popup?.closeOverlay();
  }
}
