import { Component, inject, Input } from '@angular/core';
import { combineLatest, map } from 'rxjs';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { BdButtonComponent } from '../../../../../../core/components/bd-button/bd-button.component';
import { MatTooltip } from '@angular/material/tooltip';
import { AsyncPipe } from '@angular/common';
import { BdDataColumn } from '../../../../../../../models/data';
import {
  TableCellDisplay
} from '../../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-attribute-delete-action',
    templateUrl: './attribute-delete-action.component.html',
    imports: [
        BdButtonComponent,
        MatTooltip,
        AsyncPipe,
    ],
})
export class AttributeDeleteActionComponent implements TableCellDisplay<CustomAttributeDescriptor> {
  private readonly areas = inject(NavAreasService);
  private readonly groups = inject(GroupsService);
  protected readonly settings = inject(SettingsService);

  @Input() record: CustomAttributeDescriptor;
  @Input() column: BdDataColumn<CustomAttributeDescriptor>;

  protected state$ = combineLatest([this.areas.panelRoute$, this.groups.attributeValues$]).pipe(
    map(([route, attributeValues]) => {
      const errors = [];
      const usedIn = !attributeValues
        ? ''
        : Object.keys(attributeValues)
            .filter((group) => attributeValues[group]?.attributes?.[this.record.name]?.length)
            .join(', ');
      if (usedIn.length) {
        errors.push(`Attribute is used in groups: ${usedIn}`);
      }
      if (route?.params?.['attribute'] === this.record.name) {
        errors.push(`Attribute is selected for editing`);
      }
      const tooltip = errors.length
        ? `Cannot remove attribute definition "${this.record.name}". ${errors.join('. ')}`
        : `Remove attribute definition ${this.record.name}`;
      return {
        disabled: errors.length > 0,
        tooltip,
      };
    }),
  );
}
