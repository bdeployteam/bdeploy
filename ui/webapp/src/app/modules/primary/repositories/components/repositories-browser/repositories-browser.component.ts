import { Component, OnInit } from '@angular/core';
import { SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { RepositoriesColumnsService } from '../../services/repositories-columns.service';
import { RepositoriesService } from '../../services/repositories.service';

@Component({
  selector: 'app-repositories-browser',
  templateUrl: './repositories-browser.component.html',
  styleUrls: ['./repositories-browser.component.css'],
})
export class RepositoriesBrowserComponent implements OnInit {
  /* template */ getRecordRoute = (row: SoftwareRepositoryConfiguration) => {
    return ['/repositories', 'repository', row.name];
  };

  /* template */ isCardView: boolean;
  /* template */ presetKeyValue: string = 'softwareRepositories';

  constructor(
    public repositories: RepositoriesService,
    public repositoriesColumns: RepositoriesColumnsService,
    public authenticationService: AuthenticationService,
    private cardViewService: CardViewService
  ) {}

  ngOnInit() {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }
}
