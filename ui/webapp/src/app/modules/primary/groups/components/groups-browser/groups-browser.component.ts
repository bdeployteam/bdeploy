import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Sort } from '@angular/material/sort';
import { combineLatest, Subscription } from 'rxjs';
import { BdDataColumn, bdDataDefaultSearch, BdDataGroupingDefinition } from 'src/app/models/data';
import { InstanceGroupConfigurationDto, MinionMode } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { GroupsColumnsService } from '../../services/groups-columns.service';
import { GroupsService } from '../../services/groups.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDataSortingComponent } from '../../../../core/components/bd-data-sorting/bd-data-sorting.component';
import { BdDataGroupingComponent } from '../../../../core/components/bd-data-grouping/bd-data-grouping.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { MatDivider } from '@angular/material/divider';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataDisplayComponent } from '../../../../core/components/bd-data-display/bd-data-display.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-groups-browser',
    templateUrl: './groups-browser.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDataSortingComponent, BdDataGroupingComponent, BdButtonComponent, MatDivider, BdPanelButtonComponent, BdDialogContentComponent, BdDataDisplayComponent, BdNoDataComponent, AsyncPipe]
})
export class GroupsBrowserComponent implements OnInit, OnDestroy {
  private readonly cardViewService = inject(CardViewService);
  protected readonly groups = inject(GroupsService);
  protected readonly groupColumns = inject(GroupsColumnsService);
  protected readonly config = inject(ConfigService);
  protected readonly authService = inject(AuthenticationService);

  protected grouping: BdDataGroupingDefinition<InstanceGroupConfigurationDto>[] = [];

  private subscription: Subscription;
  private isCentral = false;
  private isStandalone = false;
  protected isManaged = false;
  protected isAddAllowed = false;
  protected isAttachAllowed = false;
  protected isCardView: boolean;
  protected presetKeyValue = 'instanceGroups';
  protected sort: Sort = { active: 'name', direction: 'asc' };

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  protected getRecordRoute = (r: InstanceGroupConfigurationDto) => {
    const row = r.instanceGroupConfiguration;
    if (this.authService.isScopedExclusiveReadClient(row.name)) {
      return ['/groups', 'clients', row.name];
    }

    // in case we're managed but the group is not (yet), we're not allowed to enter the group.
    if (!row.managed && this.config.config.mode === MinionMode.MANAGED) {
      return ['', { outlets: { panel: ['panels', 'servers', 'link', 'central'] } }];
    }

    return ['/instances', 'browser', row.name];
  };

  ngOnInit(): void {
    this.subscription = this.groups.attributeDefinitions$.subscribe((attrs) => {
      this.grouping = attrs.map((attr) => {
        return {
          name: attr.description,
          group: (r) => this.groups.attributeValues$.value[r.instanceGroupConfiguration.name]?.attributes[attr.name],
        };
      });
    });
    this.subscription.add(
      combineLatest([this.config.isCentral$, this.config.isManaged$, this.config.isStandalone$]).subscribe(
        ([isCentral, isManaged, isStandalone]) => {
          this.isCentral = isCentral;
          this.isManaged = isManaged;
          this.isStandalone = isStandalone;
        },
      ),
    );
    this.isAddAllowed = this.authService.isGlobalAdmin() && (this.isCentral || this.isStandalone);
    this.isAttachAllowed = this.authService.isGlobalAdmin() && this.isManaged;
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onRecordClick(r: InstanceGroupConfigurationDto) {
    // in case we're managed but the group is not (yet), we're not allowed to enter the group - show a message instead
    const row = r.instanceGroupConfiguration;
    if (!row.managed && this.config.config.mode === MinionMode.MANAGED) {
      this.dialog
        .info(
          'Group not managed',
          `This server has been reconfigured to be <code>MANAGED</code>, however the instance group ${row.name} is not yet managed. You need to link the group with its counterpart on the <code>CENTRAL</code> server.`,
          'link',
        )
        .subscribe();
    }
  }

  protected searchInstanceGroupData(
    search: string,
    data: InstanceGroupConfigurationDto[],
    columns: BdDataColumn<InstanceGroupConfigurationDto>[],
  ) {
    return bdDataDefaultSearch(search, data, [
      ...columns,
      {
        id: 'searchableText',
        name: 'SearchableText',
        data: (r) => r.searchableText,
      },
    ]);
  }
}
