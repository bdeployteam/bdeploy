import { Component, OnInit, inject } from '@angular/core';
import { SoftwareRepositoryConfiguration } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { RepositoriesColumnsService } from '../../services/repositories-columns.service';
import { RepositoriesService } from '../../services/repositories.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { MatDivider } from '@angular/material/divider';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataDisplayComponent } from '../../../../core/components/bd-data-display/bd-data-display.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-repositories-browser',
    templateUrl: './repositories-browser.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdButtonComponent, MatDivider, BdPanelButtonComponent, BdDialogContentComponent, BdDataDisplayComponent, BdNoDataComponent, AsyncPipe]
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
