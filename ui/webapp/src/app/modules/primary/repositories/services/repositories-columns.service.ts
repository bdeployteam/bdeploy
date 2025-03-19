import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';

@Injectable({
  providedIn: 'root',
})
export class RepositoriesColumnsService {
  private readonly repositoryTypeColumn: BdDataColumn<SoftwareRepositoryConfiguration, string> = {
    id: 'type',
    name: 'Type',
    hint: BdDataColumnTypeHint.TYPE,
    data: () => 'Software Repository',
    display: BdDataColumnDisplay.CARD,
  };

  private readonly repositoryNameColumn: BdDataColumn<SoftwareRepositoryConfiguration, string> = {
    id: 'name',
    name: 'Name (Key)',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => r.name,
    isId: true,
    width: '200px',
  };

  private readonly repositoryDescriptionColumn: BdDataColumn<SoftwareRepositoryConfiguration, string> = {
    id: 'description',
    name: 'Description',
    hint: BdDataColumnTypeHint.FOOTER,
    data: (r) => r.description,
    width: '200px',
    showWhen: '(min-width: 1000px)',
  };

  private readonly repositoryLogoCardColumn: BdDataColumn<SoftwareRepositoryConfiguration, string> = {
    id: 'logo',
    name: 'Logo',
    hint: BdDataColumnTypeHint.AVATAR,
    display: BdDataColumnDisplay.CARD,
    data: () => '/assets/no-image.svg',
  };

  public readonly defaultRepositoryColumns: BdDataColumn<SoftwareRepositoryConfiguration, unknown>[] = [
    this.repositoryTypeColumn,
    this.repositoryNameColumn,
    this.repositoryDescriptionColumn,
    this.repositoryLogoCardColumn,
  ];
}
