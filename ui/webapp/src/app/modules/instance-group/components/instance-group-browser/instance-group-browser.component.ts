import { Component, OnDestroy, OnInit } from '@angular/core';
import { MediaChange, MediaObserver } from '@angular/flex-layout';
import { BehaviorSubject, forkJoin, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdSearchable, SearchService } from 'src/app/modules/core/services/search.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { DataList } from '../../../../models/dataList';
import { CustomAttributesRecord, InstanceGroupConfiguration, MinionMode } from '../../../../models/gen.dtos';
import { AuthenticationService } from '../../../core/services/authentication.service';
import { ConfigService } from '../../../core/services/config.service';
import { Logger, LoggingService } from '../../../core/services/logging.service';
import { InstanceGroupService } from '../../services/instance-group.service';

@Component({
  selector: 'app-instance-group-browser',
  templateUrl: './instance-group-browser.component.html',
  styleUrls: ['./instance-group-browser.component.css'],
  providers: [SettingsService],
})
export class InstanceGroupBrowserComponent implements OnInit, OnDestroy, BdSearchable {
  private readonly log: Logger = this.loggingService.getLogger('InstanceGroupBrowserComponent');

  private subscription: Subscription;
  private grid = new Map([
    ['xs', 1],
    ['sm', 1],
    ['md', 2],
    ['lg', 3],
    ['xl', 5],
  ]);
  // calculated number of columns
  columns = 3;

  loading = true;
  instanceGroupList: DataList<InstanceGroupConfiguration>;
  instanceGroupsAttributes: { [index: string]: CustomAttributesRecord } = {};

  recent: string[] = [];
  displayRecent: BehaviorSubject<boolean> = new BehaviorSubject(true);

  groupAttribute: string;
  groupAttributeValuesSelected: string[];

  constructor(
    private mediaObserver: MediaObserver,
    private instanceGroupService: InstanceGroupService,
    private loggingService: LoggingService,
    public authService: AuthenticationService,
    public settings: SettingsService,
    private config: ConfigService,
    private search: SearchService
  ) {}

  ngOnInit(): void {
    this.instanceGroupList = new DataList();
    this.instanceGroupList.searchCallback = (group: InstanceGroupConfiguration, text: string) => {
      if (group.name.toLowerCase().includes(text)) {
        return true;
      }
      if (group.description.toLowerCase().includes(text)) {
        return true;
      }
      const attributes: { [index: string]: string } = this.instanceGroupsAttributes[group.name].attributes;
      if (
        attributes &&
        Object.keys(attributes).find((a) => attributes[a] && attributes[a].toLowerCase().includes(text))
      ) {
        return true;
      }
      return false;
    };

    this.subscription = this.mediaObserver.asObservable().subscribe((change: MediaChange[]) => {
      // First entry has the highest priority and we can take that one
      this.columns = this.grid.get(change[0].mqAlias);
    });
    this.subscription.add(
      this.instanceGroupList.searchChange.subscribe((v) => {
        if (v === null || v.length === 0) {
          this.displayRecent.next(this.recent.length !== 0);
        } else {
          this.displayRecent.next(false);
        }
      })
    );

    this.subscription.add(this.search.register(this));

    this.loadInstanceGroups();
  }

  private loadInstanceGroups() {
    this.loading = true;
    forkJoin({
      instanceGroups: this.instanceGroupService.listInstanceGroups(),
      instanceGroupsAttributes: this.instanceGroupService.listInstanceGroupsAttributes(),
      recent: this.authService.getRecentInstanceGroups(),
    })
      .pipe(finalize(() => (this.loading = false)))
      .subscribe((r) => {
        this.instanceGroupList.addAll(r.instanceGroups);
        this.instanceGroupsAttributes = r.instanceGroupsAttributes;
        this.recent = r.recent;
        this.displayRecent.next(this.recent.length !== 0);
      });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  bdOnSearch(search: string) {
    this.instanceGroupList.searchString = search;
    this.instanceGroupList.applyFilter();
  }

  removeGroup(group: InstanceGroupConfiguration) {
    this.log.debug('got remove event');
    const index = this.recent.findIndex((r) => r === group.name);
    this.recent.splice(index, 1);
    this.instanceGroupList.remove((g) => g.name === group.name);
  }

  public filterRecent(groups: string[]): InstanceGroupConfiguration[] {
    const result: InstanceGroupConfiguration[] = [];
    for (const name of groups) {
      const index = this.instanceGroupList.data.findIndex((v) => v.name === name);
      if (index >= 0) {
        result.push(this.instanceGroupList.data[index]);
      }
    }
    return result;
  }

  isAddAllowed(): boolean {
    return this.config.config.mode === MinionMode.CENTRAL || this.config.config.mode === MinionMode.STANDALONE;
  }

  isAttachAllowed(): boolean {
    return !this.isAddAllowed();
  }

  isAttachManagedAllowed(): boolean {
    return this.config.config.mode === MinionMode.CENTRAL;
  }

  getGroupsByAttribute(attributeValue: string): InstanceGroupConfiguration[] {
    return this.instanceGroupList.filtered.filter(
      (g) => this.instanceGroupsAttributes[g.name].attributes[this.groupAttribute] == attributeValue
    );
  }
}
