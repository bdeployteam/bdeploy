import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';
import { RepositoriesService } from './repositories.service';

@Injectable({
  providedIn: 'root',
})
export class RepositoriesColumnsService {
  repositoryTypeColumn: BdDataColumn<SoftwareRepositoryConfiguration> = {
    id: 'type',
    name: 'Type',
    hint: BdDataColumnTypeHint.TYPE,
    data: (r) => 'Software Repository',
    display: BdDataColumnDisplay.CARD,
  };

  repositoryNameColumn: BdDataColumn<SoftwareRepositoryConfiguration> = {
    id: 'name',
    name: 'Name (Key)',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => r.name,
    width: '200px',
    showWhen: '(min-width: 700px)',
  };

  repositoryDescriptionColumn: BdDataColumn<SoftwareRepositoryConfiguration> = {
    id: 'description',
    name: 'Description',
    hint: BdDataColumnTypeHint.FOOTER,
    data: (r) => r.description,
    width: '200px',
    showWhen: '(min-width: 1000px)',
  };

  repositoryLogoCardColumn: BdDataColumn<SoftwareRepositoryConfiguration> = {
    id: 'logo',
    name: 'Logo',
    hint: BdDataColumnTypeHint.AVATAR,
    display: BdDataColumnDisplay.CARD,
    data: (r) => '/assets/repository.svg',
  };

  defaultRepositoryColumns: BdDataColumn<SoftwareRepositoryConfiguration>[] = [
    this.repositoryTypeColumn,
    this.repositoryNameColumn,
    this.repositoryDescriptionColumn,
    this.repositoryLogoCardColumn,
  ];

  constructor(private repositories: RepositoriesService) {}
}
