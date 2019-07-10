import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { SORT_PURPOSE } from '../models/consts';
import { ClientApplicationDto, InstanceClientAppsDto, InstanceConfiguration, InstancePurpose, LauncherDto, OperatingSystem } from '../models/gen.dtos';
import { DownloadService } from '../services/download.service';
import { InstanceGroupService } from '../services/instance-group.service';
import { InstanceService } from '../services/instance.service';
import { SoftwareUpdateService } from '../services/software-update.service';

@Component({
  selector: 'app-client-apps',
  templateUrl: './client-apps.component.html',
  styleUrls: ['./client-apps.component.css'],
})
export class ClientAppsComponent implements OnInit {
  instanceGroupName: string = this.route.snapshot.paramMap.get('group');
  loading = true;
  hasApps = false;
  hasLaunchers = false;
  instanceApps: InstanceClientAppsDto[];

  launcherLoading = true;
  launcherDto: LauncherDto;

  constructor(
    public route: ActivatedRoute,
    public location: Location,
    public instanceGroupService: InstanceGroupService,
    public instanceService: InstanceService,
    public downloadService: DownloadService,
    public updateService: SoftwareUpdateService,
  ) {}

  ngOnInit() {
    const instancePromise = this.instanceGroupService.listClientApps(this.instanceGroupName);
    instancePromise.pipe(finalize(() => (this.loading = false))).subscribe(apps => {
      this.onClientAppsLoaded(apps);
    });

    const launcherPromise = this.updateService.getLatestLaunchers();
    launcherPromise.pipe(finalize(() => (this.launcherLoading = false))).subscribe(launchers => {
      this.onLauncherLoaded(launchers);
    });
  }

  onClientAppsLoaded(apps: InstanceClientAppsDto[]) {
    this.instanceApps = apps;
    this.hasApps = apps.length > 0;
  }

  onLauncherLoaded(launchers: LauncherDto) {
    this.launcherDto = launchers;
    this.hasLaunchers = Object.keys(launchers.launchers).length > 0;
  }

  downloadLauncher(os: OperatingSystem) {
    const key = this.launcherDto.launchers[os];
    window.location.href = this.updateService.getDownloadUrl(key);
  }

  downloadClickAndRun(instance: InstanceConfiguration, app: ClientApplicationDto) {
    this.instanceService
      .createClickAndStartDescriptor(this.instanceGroupName, instance.uuid, app.uuid)
      .subscribe(data => {
        this.downloadService.downloadData(app.description + '.bdeploy', data);
      });
  }

  downloadInstaller(instance: InstanceConfiguration, app: ClientApplicationDto) {
    this.instanceService.createClientInstaller(this.instanceGroupName, instance.uuid, app.uuid).subscribe(token => {
      window.location.href = this.instanceService.downloadClientInstaller(
        this.instanceGroupName,
        instance.uuid,
        app.uuid,
        token,
      );
    });
  }

  getPurposes(): InstancePurpose[] {
    const purpose: InstancePurpose[] = [];
    for (const app of this.instanceApps) {
      const instancePurpose = app.instance.purpose;
      if (purpose.includes(instancePurpose)) {
        continue;
      }
      purpose.push(instancePurpose);
    }

    // Sort by purpose priority
    return purpose.sort(SORT_PURPOSE);
  }

  getInstances(purpose: InstancePurpose): InstanceConfiguration[] {
    const filtered: InstanceConfiguration[] = [];
    for (const instanceApp of this.instanceApps) {
      const instance = instanceApp.instance;
      if (instance.purpose !== purpose) {
        continue;
      }
      filtered.push(instance);
    }
    // Sort by name
    return filtered.sort((a, b) => {
      return a.name.localeCompare(b.name);
    });
  }

  getApps(instance: InstanceConfiguration): ClientApplicationDto[] {
    let filtered: ClientApplicationDto[] = [];
    for (const instanceApp of this.instanceApps) {
      if (instanceApp.instance !== instance) {
        continue;
      }
      filtered = filtered.concat(instanceApp.applications);
    }
    // Sort by OS (Windows first), then name
    return filtered.sort((a, b) => {
      const compareOs = b.os.localeCompare(a.os);
      if (compareOs !== 0) {
        return compareOs;
      }
      return a.description.localeCompare(b.description);
    });
  }
}
