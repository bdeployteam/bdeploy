import { Component, OnDestroy, OnInit } from '@angular/core';
import { MediaChange, MediaObserver } from '@angular/flex-layout';
import { BehaviorSubject, Subscription } from 'rxjs';
import { DataList } from '../models/dataList';
import { InstanceGroupConfiguration, MinionMode } from '../models/gen.dtos';
import { AuthenticationService } from '../services/authentication.service';
import { ConfigService } from '../services/config.service';
import { InstanceGroupService } from '../services/instance-group.service';
import { Logger, LoggingService } from '../services/logging.service';

@Component({
  selector: 'app-instance-group-browser',
  templateUrl: './instance-group-browser.component.html',
  styleUrls: ['./instance-group-browser.component.css']
})
export class InstanceGroupBrowserComponent implements OnInit, OnDestroy {

  log: Logger = this.loggingService.getLogger('InstanceGroupBrowserComponent');

  private subscription: Subscription;

  private grid = new Map([
    ['xs', 1],
    ['sm', 1],
    ['md', 2],
    ['lg', 3],
    ['xl', 5]
  ]);
  // calculated number of columns
  columns = 3;

  instanceGroupList: DataList<InstanceGroupConfiguration>;
  recent: String[] = [];
  displayRecent$: BehaviorSubject<boolean> = new BehaviorSubject(true);

  constructor(
    private mediaObserver: MediaObserver,
    private instanceGroupService: InstanceGroupService,
    private loggingService: LoggingService,
    private authService: AuthenticationService,
    private config: ConfigService) { }

  ngOnInit(): void {
    this.log.debug('intializing...');
    this.instanceGroupList = new DataList();
    this.instanceGroupList.searchCallback = (group: InstanceGroupConfiguration, text: string) => {
      if (group.name.toLowerCase().includes(text)) {
        return true;
      }
      if (group.description.toLowerCase().includes(text)) {
        return true;
      }
      return false;
    };

    this.subscription = this.mediaObserver.media$.subscribe((change: MediaChange) => {
      this.columns = this.grid.get(change.mqAlias);
    });

    this.subscription.add(this.instanceGroupList.searchChange.subscribe(v => {
      if (v === null || v.length === 0) {
        this.displayRecent$.next(this.recent.length !== 0);
      } else {
        this.displayRecent$.next(false);
      }
    }));

    this.loadInstanceGroups();
  }

  private loadInstanceGroups() {
    this.instanceGroupService.listInstanceGroups().subscribe(instanceGroups => {
      this.instanceGroupList.addAll(instanceGroups);
      this.log.debug('got ' + instanceGroups.length + ' instance groups');
    });

    this.authService.getRecentInstanceGroups().subscribe(recent => {
      this.recent = recent;
      this.log.debug('got ' + recent.length + ' recent instance groups');
      this.displayRecent$.next(this.recent.length !== 0);
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  removeGroup(group: InstanceGroupConfiguration) {
    this.log.debug('got remove event');
    const index = this.recent.findIndex(r => r === group.name);
    this.recent.splice(index, 1);

    // remove by name.
    this.instanceGroupList.remove(g => g.name === group.name);
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
}
