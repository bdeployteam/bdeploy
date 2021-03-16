import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
import { GroupsService } from './groups.service';

@Injectable({
  providedIn: 'root',
})
export class GroupsColumnsService {
  groupTypeColumn: BdDataColumn<InstanceGroupConfiguration> = {
    id: 'type',
    name: 'Type',
    hint: BdDataColumnTypeHint.TYPE,
    data: (r) => 'Instance Group',
    display: BdDataColumnDisplay.CARD,
  };

  groupNameColumn: BdDataColumn<InstanceGroupConfiguration> = {
    id: 'name',
    name: 'Name (Key)',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => r.name,
    width: '200px',
    showWhen: '(min-width: 700px)',
  };

  groupTitleColumn: BdDataColumn<InstanceGroupConfiguration> = {
    id: 'title',
    name: 'Title',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.title,
  };

  groupDescriptionColumn: BdDataColumn<InstanceGroupConfiguration> = {
    id: 'description',
    name: 'Description',
    hint: BdDataColumnTypeHint.FOOTER,
    data: (r) => r.description,
    showWhen: '(min-width: 1000px)',
  };

  groupLogoTableColumn: BdDataColumn<InstanceGroupConfiguration> = {
    id: 'logo',
    name: 'Logo',
    hint: BdDataColumnTypeHint.AVATAR,
    display: BdDataColumnDisplay.TABLE,
    data: (r) => this.groups.getLogoUrlOrDefault(r.name, r.logo, null),
    width: '150px',
  };

  groupLogoCardColumn: BdDataColumn<InstanceGroupConfiguration> = {
    id: 'logo',
    name: 'Logo',
    hint: BdDataColumnTypeHint.AVATAR,
    display: BdDataColumnDisplay.CARD,
    data: (r) => this.groups.getLogoUrlOrDefault(r.name, r.logo, '/assets/no-image.svg'),
  };

  defaultGroupColumns: BdDataColumn<InstanceGroupConfiguration>[] = [
    this.groupTypeColumn,
    this.groupNameColumn,
    this.groupTitleColumn,
    this.groupDescriptionColumn,
    this.groupLogoTableColumn,
    this.groupLogoCardColumn,
  ];

  constructor(private groups: GroupsService) {}
}
