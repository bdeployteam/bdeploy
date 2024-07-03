import { Injectable, inject } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { InstanceGroupConfigurationDto } from 'src/app/models/gen.dtos';
import { GroupsService } from './groups.service';

@Injectable({
  providedIn: 'root',
})
export class GroupsColumnsService {
  private readonly groups = inject(GroupsService);

  private readonly groupTypeColumn: BdDataColumn<InstanceGroupConfigurationDto> = {
    id: 'type',
    name: 'Type',
    hint: BdDataColumnTypeHint.TYPE,
    data: () => 'Instance Group',
    display: BdDataColumnDisplay.CARD,
  };

  private readonly groupNameColumn: BdDataColumn<InstanceGroupConfigurationDto> = {
    id: 'name',
    name: 'Name (Key)',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => r.instanceGroupConfiguration.name,
    isId: true,
    width: '200px',
    showWhen: '(min-width: 700px)',
    sortCard: true,
  };

  private readonly groupTitleColumn: BdDataColumn<InstanceGroupConfigurationDto> = {
    id: 'title',
    name: 'Title',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.instanceGroupConfiguration.title,
    sortCard: true,
  };

  private readonly groupDescriptionColumn: BdDataColumn<InstanceGroupConfigurationDto> = {
    id: 'description',
    name: 'Description',
    hint: BdDataColumnTypeHint.FOOTER,
    data: (r) => r.instanceGroupConfiguration.description,
    showWhen: '(min-width: 1000px)',
  };

  private readonly groupLogoTableColumn: BdDataColumn<InstanceGroupConfigurationDto> = {
    id: 'logo',
    name: 'Logo',
    hint: BdDataColumnTypeHint.AVATAR,
    display: BdDataColumnDisplay.TABLE,
    data: (r) =>
      this.groups.getLogoUrlOrDefault(r.instanceGroupConfiguration.name, r.instanceGroupConfiguration.logo, null),
    width: '150px',
  };

  private readonly groupLogoCardColumn: BdDataColumn<InstanceGroupConfigurationDto> = {
    id: 'logo',
    name: 'Logo',
    hint: BdDataColumnTypeHint.AVATAR,
    display: BdDataColumnDisplay.CARD,
    data: (r) =>
      this.groups.getLogoUrlOrDefault(
        r.instanceGroupConfiguration.name,
        r.instanceGroupConfiguration.logo,
        '/assets/no-image.svg',
      ),
  };

  public readonly defaultGroupColumns: BdDataColumn<InstanceGroupConfigurationDto>[] = [
    this.groupTypeColumn,
    this.groupNameColumn,
    this.groupTitleColumn,
    this.groupDescriptionColumn,
    this.groupLogoTableColumn,
    this.groupLogoCardColumn,
  ];
}
