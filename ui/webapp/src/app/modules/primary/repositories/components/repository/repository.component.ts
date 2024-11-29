import { Component, OnInit, inject } from '@angular/core';
import { BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { SoftwareDetailsBulkService } from 'src/app/modules/panels/repositories/services/software-details-bulk.service';
import { RepositoriesService } from '../../services/repositories.service';
import { RepositoryColumnsService } from '../../services/repository-columns.service';
import { RepositoryService, SwDtoWithType, SwPkgCompound, SwPkgType } from '../../services/repository.service';

@Component({
    selector: 'app-repository',
    templateUrl: './repository.component.html',
    standalone: false
})
export class RepositoryComponent implements OnInit {
  private readonly cardViewService = inject(CardViewService);
  protected readonly repositories = inject(RepositoriesService);
  protected readonly repository = inject(RepositoryService);
  protected readonly repositoryColumns = inject(RepositoryColumnsService);
  protected readonly auth = inject(AuthenticationService);
  protected readonly bulk = inject(SoftwareDetailsBulkService);

  protected grouping: BdDataGroupingDefinition<any>[] = [{ name: 'Type', group: (r) => r.type }];
  protected defaultGrouping: BdDataGrouping<unknown>[] = [{ definition: this.grouping[0], selected: [] }];

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

  protected checkChangeForbidden(software: SwPkgCompound): boolean {
    return software.type === SwPkgType.EXTERNAL_SOFTWARE && (software as SwDtoWithType).requiredByProduct;
  }
}
