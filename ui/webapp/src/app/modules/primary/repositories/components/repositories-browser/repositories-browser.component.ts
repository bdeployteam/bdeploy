import { Component, OnInit, inject } from '@angular/core';
import { SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { RepositoriesColumnsService } from '../../services/repositories-columns.service';
import { RepositoriesService } from '../../services/repositories.service';

@Component({
  selector: 'app-repositories-browser',
  templateUrl: './repositories-browser.component.html',
})
export class RepositoriesBrowserComponent implements OnInit {
  private cardViewService = inject(CardViewService);
  protected repositories = inject(RepositoriesService);
  protected repositoriesColumns = inject(RepositoriesColumnsService);
  protected authenticationService = inject(AuthenticationService);

  protected getRecordRoute = (row: SoftwareRepositoryConfiguration) => ['/repositories', 'repository', row.name];

  protected isCardView: boolean;
  protected presetKeyValue = 'softwareRepositories';

  ngOnInit() {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }
}
