import { Component, OnInit, inject } from '@angular/core';
import { SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { RepositoriesColumnsService } from '../../services/repositories-columns.service';
import { RepositoriesService } from '../../services/repositories.service';

@Component({
    selector: 'app-repositories-browser',
    templateUrl: './repositories-browser.component.html',
    standalone: false
})
export class RepositoriesBrowserComponent implements OnInit {
  private readonly cardViewService = inject(CardViewService);
  protected readonly repositories = inject(RepositoriesService);
  protected readonly repositoriesColumns = inject(RepositoriesColumnsService);
  protected readonly authenticationService = inject(AuthenticationService);

  protected getRecordRoute = (row: SoftwareRepositoryConfiguration) => ['/repositories', 'repository', row.name];

  protected isCardView: boolean;
  protected presetKeyValue = 'softwareRepositories';

  ngOnInit() {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }
}
