import { Component, OnInit, inject } from '@angular/core';
import { BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { RepositoriesService } from '../../services/repositories.service';
import { RepositoryColumnsService } from '../../services/repository-columns.service';
import { RepositoryService } from '../../services/repository.service';

@Component({
  selector: 'app-repository',
  templateUrl: './repository.component.html',
})
export class RepositoryComponent implements OnInit {
  private cardViewService = inject(CardViewService);
  protected repositories = inject(RepositoriesService);
  protected repository = inject(RepositoryService);
  protected repositoryColumns = inject(RepositoryColumnsService);
  protected auth = inject(AuthenticationService);

  protected grouping: BdDataGroupingDefinition<any>[] = [{ name: 'Type', group: (r) => r.type }];
  protected defaultGrouping: BdDataGrouping<any>[] = [{ definition: this.grouping[0], selected: [] }];

  protected getRecordRoute = (row: any) => {
    return [
      '',
      {
        outlets: {
          panel: ['panels', 'repositories', 'details', row.key.name, row.key.tag],
        },
      },
    ];
  };

  protected isCardView: boolean;
  protected presetKeyValue = 'software-repository';

  ngOnInit() {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }
}
