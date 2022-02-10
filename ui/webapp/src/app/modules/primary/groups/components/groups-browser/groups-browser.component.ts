import { Component, OnDestroy, OnInit } from '@angular/core';
import { combineLatest, Subscription } from 'rxjs';
import { BdDataGroupingDefinition } from 'src/app/models/data';
import { InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { GroupsColumnsService } from '../../services/groups-columns.service';
import { GroupsService } from '../../services/groups.service';

@Component({
  selector: 'app-groups-browser',
  templateUrl: './groups-browser.component.html',
  styleUrls: ['./groups-browser.component.css'],
})
export class GroupsBrowserComponent implements OnInit, OnDestroy {
  grouping: BdDataGroupingDefinition<InstanceGroupConfiguration>[] = [];

  private subscription: Subscription;
  private isCentral: boolean = false;
  /* template */ public isManaged: boolean = false;
  private isStandalone: boolean = false;
  /* template */ public isAddAllowed: boolean = false;
  /* template */ public isAttachAllowed: boolean = false;
  /* template */ public isCardView: boolean;
  /* template */ public presetKeyValue: string = 'instanceGroups';

  /* template */ getRecordRoute = (row: InstanceGroupConfiguration) => {
    if (this.authService.isScopedExclusiveReadClient(row.name)) {
      return ['/groups', 'clients', row.name];
    }
    return ['/instances', 'browser', row.name];
  };

  constructor(
    public groups: GroupsService,
    public groupColumns: GroupsColumnsService,
    public config: ConfigService,
    public authService: AuthenticationService,
    private cardViewService: CardViewService
  ) {}

  ngOnInit(): void {
    this.subscription = this.groups.attributeDefinitions$.subscribe((attrs) => {
      this.grouping = attrs.map((attr) => {
        return {
          name: attr.description,
          group: (r) => this.groups.attributeValues$.value[r.name]?.attributes[attr.name],
        };
      });
    });
    this.subscription.add(
      combineLatest([this.config.isCentral$, this.config.isManaged$, this.config.isStandalone$]).subscribe(([isCentral, isManaged, isStandalone]) => {
        this.isCentral = isCentral;
        this.isManaged = isManaged;
        this.isStandalone = isStandalone;
      })
    );
    this.isAddAllowed = this.authService.isGlobalAdmin() && (this.isCentral || this.isStandalone);
    this.isAttachAllowed = this.authService.isGlobalAdmin() && this.isManaged;
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
