import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { combineLatest, Subscription } from 'rxjs';
import { BdDataGroupingDefinition } from 'src/app/models/data';
import {
  InstanceGroupConfiguration,
  MinionMode,
} from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { GroupsColumnsService } from '../../services/groups-columns.service';
import { GroupsService } from '../../services/groups.service';

@Component({
  selector: 'app-groups-browser',
  templateUrl: './groups-browser.component.html',
})
export class GroupsBrowserComponent implements OnInit, OnDestroy {
  grouping: BdDataGroupingDefinition<InstanceGroupConfiguration>[] = [];

  private subscription: Subscription;
  private isCentral = false;
  /* template */ public isManaged = false;
  private isStandalone = false;
  /* template */ public isAddAllowed = false;
  /* template */ public isAttachAllowed = false;
  /* template */ public isCardView: boolean;
  /* template */ public presetKeyValue = 'instanceGroups';

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  /* template */ getRecordRoute = (row: InstanceGroupConfiguration) => {
    if (this.authService.isScopedExclusiveReadClient(row.name)) {
      return ['/groups', 'clients', row.name];
    }

    // in case we're managed but the group is not (yet), we're not allowed to enter the group.
    if (!row.managed && this.config.config.mode === MinionMode.MANAGED) {
      return [
        '',
        { outlets: { panel: ['panels', 'servers', 'link', 'central'] } },
      ];
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
          group: (r) =>
            this.groups.attributeValues$.value[r.name]?.attributes[attr.name],
        };
      });
    });
    this.subscription.add(
      combineLatest([
        this.config.isCentral$,
        this.config.isManaged$,
        this.config.isStandalone$,
      ]).subscribe(([isCentral, isManaged, isStandalone]) => {
        this.isCentral = isCentral;
        this.isManaged = isManaged;
        this.isStandalone = isStandalone;
      })
    );
    this.isAddAllowed =
      this.authService.isGlobalAdmin() && (this.isCentral || this.isStandalone);
    this.isAttachAllowed = this.authService.isGlobalAdmin() && this.isManaged;
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  onRecordClick(row: InstanceGroupConfiguration) {
    // in case we're managed but the group is not (yet), we're not allowed to enter the group - show a message instead
    if (!row.managed && this.config.config.mode === MinionMode.MANAGED) {
      this.dialog
        .info(
          'Group not managed',
          `This server has been reconfigured to be <code>MANAGED</code>, however the instance group ${row.name} is not yet managed. You need to link the group with its counterpart on the <code>CENTRAL</code> server.`,
          'link'
        )
        .subscribe();
    }
  }
}
