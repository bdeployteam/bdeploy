import { Component, Input, OnInit } from '@angular/core';
import { combineLatest, map, Observable } from 'rxjs';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';

@Component({
  selector: 'app-bd-activity-instance-footer',
  templateUrl: './bd-activity-instance-footer.component.html',
})
export class BdActivityInstanceFooterComponent implements OnInit {
  @Input() scope: string[];
  footer$: Observable<string>;

  constructor(
    private groupsService: GroupsService,
    private instancesService: InstancesService
  ) {}

  ngOnInit(): void {
    this.footer$ = combineLatest([
      this.groupsService.groups$,
      this.instancesService.instances$,
    ]).pipe(
      map(([groups, instances]) => {
        if (this.scope.length === 0) {
          return 'Global - ';
        }
        if (this.scope.length === 1 && this.scope[1] === 'default') {
          return 'Global - ';
        }
        const [groupName, instanceUuid] = this.scope;
        const groupTitle = groups.find(
          (g) => g.instanceGroupConfiguration.name === groupName
        )?.instanceGroupConfiguration?.title;
        const instanceName = instances.find(
          (i) => i.instanceConfiguration.id === instanceUuid
        )?.instanceConfiguration?.name;
        return [instanceName, groupTitle]
          .filter((str) => !!str)
          .map((str) => `${str} - `)
          .join('');
      })
    );
  }
}
