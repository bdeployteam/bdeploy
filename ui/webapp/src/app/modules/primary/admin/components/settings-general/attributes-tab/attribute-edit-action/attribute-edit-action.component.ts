import { Component, Input } from '@angular/core';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';
import { BdPanelButtonComponent } from '../../../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdDataColumn } from '../../../../../../../models/data';
import {
  TableCellDisplay
} from '../../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-attribute-edit-action',
    templateUrl: './attribute-edit-action.component.html',
    imports: [BdPanelButtonComponent],
})
export class AttributeEditActionComponent implements TableCellDisplay<CustomAttributeDescriptor> {
  @Input() record: CustomAttributeDescriptor;
  @Input() column: BdDataColumn<CustomAttributeDescriptor>;
}
