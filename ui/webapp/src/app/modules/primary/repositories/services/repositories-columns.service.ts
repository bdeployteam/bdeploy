import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';

@Injectable({
  providedIn: 'root',
})
export class RepositoriesColumnsService {
  private repositoryTypeColumn: BdDataColumn<SoftwareRepositoryConfiguration> = {
    id: 'type',
    name: 'Type',
    hint: BdDataColumnTypeHint.TYPE,
    data: () => 'Software Repository',
    display: BdDataColumnDisplay.CARD,
  };

  private repositoryNameColumn: BdDataColumn<SoftwareRepositoryConfiguration> = {
    id: 'name',
    name: 'Name (Key)',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => r.name,
    isId: true,
    width: '200px',
  };

  private repositoryDescriptionColumn: BdDataColumn<SoftwareRepositoryConfiguration> = {
    id: 'description',
    name: 'Description',
    hint: BdDataColumnTypeHint.FOOTER,
    data: (r) => r.description,
    width: '200px',
    showWhen: '(min-width: 1000px)',
  };

  private repositoryLogoCardColumn: BdDataColumn<SoftwareRepositoryConfiguration> = {
    id: 'logo',
    name: 'Logo',
    hint: BdDataColumnTypeHint.AVATAR,
    display: BdDataColumnDisplay.CARD,
    data: () => '/assets/no-image.svg',
  };

  public defaultRepositoryColumns: BdDataColumn<SoftwareRepositoryConfiguration>[] = [
    this.repositoryTypeColumn,
    this.repositoryNameColumn,
    this.repositoryDescriptionColumn,
    this.repositoryLogoCardColumn,
  ];
}
