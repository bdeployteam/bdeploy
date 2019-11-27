import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { SORT_PURPOSE } from '../models/consts';
import { ClientApplicationDto, InstanceClientAppsDto, InstanceConfiguration, InstancePurpose, OperatingSystem } from '../models/gen.dtos';
import { SoftwareUpdateService } from '../modules/admin/services/software-update.service';
import { DownloadService } from '../services/download.service';
import { InstanceGroupService } from '../services/instance-group.service';
import { InstanceService } from '../services/instance.service';
import { LauncherService } from '../services/launcher.service';

@Component({
  selector: 'app-client-apps',
  templateUrl: './client-apps.component.html',
  styleUrls: ['./client-apps.component.css'],
  providers: [LauncherService],
})
export class ClientAppsComponent implements OnInit {

  instanceGroupName: string = this.route.snapshot.paramMap.get('group');
  instanceApps: InstanceClientAppsDto[];

  loading = true;
  hasApps = false;
  activeOs: OperatingSystem;

  constructor(
    public route: ActivatedRoute,
    public location: Location,
    public instanceGroupService: InstanceGroupService,
    public instanceService: InstanceService,
    public downloadService: DownloadService,
    public updateService: SoftwareUpdateService,
    public launcherService: LauncherService,
  ) {}

  ngOnInit() {
    this.activeOs = this.launcherService.getRunningOs();
    this.loadApps();
  }

  getAllOs() {
    // TODO: include MACOS once MACOS support is complete
    return Object.keys(OperatingSystem).filter(x => x !== OperatingSystem.UNKNOWN && x !== OperatingSystem.AIX && x !== OperatingSystem.MACOS);
  }

  loadApps() {
    this.loading = true;
    const instancePromise = this.instanceGroupService.listClientApps(this.instanceGroupName, this.activeOs);
    instancePromise.pipe(finalize(() => (this.loading = false))).subscribe(apps => {
      this.onClientAppsLoaded(apps);
    });
  }

  onClientAppsLoaded(apps: InstanceClientAppsDto[]) {
    this.instanceApps = apps;
    this.hasApps = apps.length > 0;
  }

  isLoading() {
    return this.loading || this.launcherService.loading;
  }

  switchOs(os: OperatingSystem) {
    this.activeOs = os;
    this.loadApps();
  }

  downloadLauncherInstaller() {
    const promise = this.updateService.createLauncherInstaller(this.activeOs);
    promise.subscribe(token => {
      const downloadUrl = this.downloadService.createDownloadUrl(token);
      this.downloadService.download(downloadUrl);
    });
  }

  downloadLauncherZip() {
    const key = this.launcherService.getLauncherForOs(this.activeOs);
    this.downloadService.download(this.updateService.getDownloadUrl(key));
  }

  downloadClickAndRun(instance: InstanceConfiguration, app: ClientApplicationDto) {
    this.instanceService
      .createClickAndStartDescriptor(this.instanceGroupName, instance.uuid, app.uuid)
      .subscribe(data => {
        this.downloadService.downloadJson(app.description + '.bdeploy', data);
      });
  }

  downloadInstaller(instance: InstanceConfiguration, app: ClientApplicationDto) {
    this.instanceService.createClientInstaller(this.instanceGroupName, instance.uuid, app.uuid).subscribe(token => {
      this.instanceService.downloadClientInstaller(token);
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

    // Sort by purpose
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
    return filtered.sort((a, b) => {
      return a.description.localeCompare(b.description);
    });
  }
}
