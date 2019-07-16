import { Injectable } from '@angular/core';
import { DeviceDetectorService } from 'ngx-device-detector';
import { finalize } from 'rxjs/operators';
import { LauncherDto, ManifestKey, OperatingSystem } from '../models/gen.dtos';
import { getAppOs } from '../utils/manifest.utils';
import { SoftwareUpdateService } from './software-update.service';

@Injectable()
export class LauncherService {

  /** Whether or not the launchers have been loaded from the backend. */
  public loading = true;

  /** Latest launchers from backend */
  public launcherDto: LauncherDto;

  /**
   * Loads the latest available launchers
   */
  constructor(private updateService: SoftwareUpdateService, private deviceService: DeviceDetectorService) {
    const launcherPromise = this.updateService.getLatestLaunchers();
    launcherPromise.pipe(finalize(() => (this.loading = false))).subscribe(launchers => {
      this.launcherDto = launchers;
    });
  }

  /**
   * Returns whether or not at least one launcher is available
   */
  public hasLaunchers(): boolean {
    return this.getLaunchers().length > 0;
  }

  /**
   * Returns an array containing the available launchers.
   */
  public getLaunchers(): ManifestKey[] {
    return Object.values(this.launcherDto.launchers);
  }

  /**
   * Returns a list of operating systems for which launchers are available.
   */
  public getSupportedOs(): OperatingSystem[] {
    const os: OperatingSystem[] = [];
    for (const key of this.getLaunchers()) {
      os.push(getAppOs(key));
    }
    return os;
  }

  /**
   * Returns the manifest key to load the launcher for the given OS.
   */
  public getLauncherForOs(os: OperatingSystem): ManifestKey {
    return this.launcherDto.launchers[os];
  }

  /**
   * Returns whether or not launchers for the current running OS are available
   */
  public hasLauncherForOs(os: OperatingSystem): boolean {
    return this.launcherDto.launchers[os] !== undefined;
  }

  /**
   * Returns the running operating system as enumeration
   */
  public getRunningOs() {
    const runningOs = this.deviceService.os;
    if (runningOs === 'Windows') {
      return OperatingSystem.WINDOWS;
    } else if (runningOs === 'Linux') {
      return OperatingSystem.LINUX;
    }
    return OperatingSystem.UNKNOWN;
  }
}
