import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { BdDataColumn, BdDataGrouping } from 'src/app/models/data';
import {
  ApplicationConfiguration,
  ApplicationDto,
  InstanceConfigurationDto,
  SystemConfiguration,
} from 'src/app/models/gen.dtos';
import { BdPopupDirective } from 'src/app/modules/core/components/bd-popup/bd-popup.directive';
import {
  LinkVariable,
  gatherPathExpansions,
  gatherProcessExpansions,
  gatherSpecialExpansions,
  gatherVariableExpansions,
} from 'src/app/modules/core/utils/linked-values.utils';

const colVarName: BdDataColumn<LinkVariable> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  isId: true,
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
  selector: 'app-bd-expression-picker',
  templateUrl: './bd-expression-picker.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdExpressionPickerComponent implements OnChanges {
  @Input() process: ApplicationConfiguration;
  @Input() instance: InstanceConfigurationDto;
  @Input() system: SystemConfiguration;
  @Input() applications: ApplicationDto[];
  @Input() popup: BdPopupDirective;

  @Output() linkSelected = new EventEmitter<string>();

  protected varRecords: LinkVariable[];
  /* temlpate */ paramRecords: LinkVariable[];
  protected pathRecords: LinkVariable[];
  protected specialRecords: LinkVariable[];

  protected varColumns: BdDataColumn<LinkVariable>[] = [colVarName, colVarValue, colVarDesc];
  /* temlpate */ paramGrouping: BdDataGrouping<LinkVariable>[] = [
    {
      definition: { group: (r) => r.group, name: 'Application' },
      selected: [],
    },
  ];

  ngOnChanges(): void {
    // instance variables take precedence over system variables
    this.varRecords = gatherVariableExpansions(this.instance, this.system);
    this.paramRecords = gatherProcessExpansions(this.instance, this.process, this.applications);
    this.pathRecords = gatherPathExpansions();
    this.specialRecords = gatherSpecialExpansions(this.instance, this.process, this.system);
  }

  protected onSelect(v: LinkVariable) {
    this.linkSelected.emit(v.link);
    this.popup?.closeOverlay();
  }
}
