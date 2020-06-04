import { Component, OnInit } from '@angular/core';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { finalize } from 'rxjs/operators';
import { BackendInfoDto, ManifestKey, OperatingSystem } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { FileUploadComponent } from 'src/app/modules/shared/components/file-upload/file-upload.component';
import { MessageBoxMode } from 'src/app/modules/shared/components/messagebox/messagebox.component';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';
import { convert2String } from 'src/app/modules/shared/utils/version.utils';
import { SoftwareUpdateService } from '../../services/software-update.service';
import { UpdateDialogComponent } from '../update-dialog/update-dialog.component';

export class GroupedKeys {
  public tag: String;
  public snapshot: boolean;
  public current: boolean;
  public oss: OperatingSystem[];
  public keys: ManifestKey[];
}

@Component({
  selector: 'app-update-browser',
  templateUrl: './update-browser.component.html',
  styleUrls: ['./update-browser.component.css'],
})
export class UpdateBrowserComponent implements OnInit {

  public systemVersions: GroupedKeys[];
  public launcherVersions: GroupedKeys[];
  public backendInfo: BackendInfoDto;

  public systemLoading = false;
  public launcherLoading = false;

  constructor(private updService: SoftwareUpdateService, private cfgService: ConfigService, private mbService: MessageboxService, private dialog: MatDialog) {}

  ngOnInit() {
    this.systemLoading = true;
    this.launcherLoading = true;
    this.reload();
  }

  private async reload() {
    this.backendInfo = await this.cfgService.getBackendInfo().toPromise();
    this.updService
      .listBDeployVersions()
      .pipe(finalize(() => (this.systemLoading = false)))
      .subscribe(r => (this.systemVersions = this.groupKeysByTag(r)));
    this.updService
      .listLauncherVersions()
      .pipe(finalize(() => (this.launcherLoading = false)))
      .subscribe(r => (this.launcherVersions = this.groupKeysByTag(r)));
  }

  private groupKeysByTag(keys: ManifestKey[]) {
    const tags: { [key: string]: GroupedKeys } = {};

    // keys are already sorted by the backend. reverse the order to have the newest version on top.
    const currentVersion = this.backendInfo.version;
    keys.reverse().forEach(k => {
      if (!(k.tag in tags)) {
        tags[k.tag] = new GroupedKeys();
      }
      const group = tags[k.tag];
      group.tag = k.tag;
      group.snapshot = k.name.includes('snapshot');

      if (convert2String(currentVersion) === group.tag) {
        group.current = true;
      }

      if (!group.oss) {
        group.oss = [];
      }
      if (!group.keys) {
        group.keys = [];
      }

      group.oss.push(this.determineOs(k.name));
      group.keys.push(k);
    });

    return Object.values(tags);
  }

  private determineOs(name: string): OperatingSystem {
    const upper = name.toUpperCase();
    if (upper.endsWith(OperatingSystem.WINDOWS)) {
      return OperatingSystem.WINDOWS;
    }
    if (upper.endsWith(OperatingSystem.LINUX)) {
      return OperatingSystem.LINUX;
    }
    if (upper.endsWith(OperatingSystem.AIX)) {
      return OperatingSystem.AIX;
    }
    if (upper.endsWith(OperatingSystem.MACOS)) {
      return OperatingSystem.MACOS;
    }
    return OperatingSystem.UNKNOWN;
  }

  async deleteSystemVersion(keys: GroupedKeys) {
    const doIt = await this.mbService.openAsync({title: 'Delete?', message: `Delete ${keys.keys.length} associated software packages from the server?`, mode: MessageBoxMode.QUESTION});
    if (doIt) {
      await this.updService.deleteVersion(keys.keys).toPromise();
    }
    this.reload();
  }

  async deleteLauncherVersion(keys: GroupedKeys) {
    const doIt = await this.mbService.openAsync({title: 'Delete?', message: `Delete ${keys.keys.length} associated launcher packages from the server?`, mode: MessageBoxMode.QUESTION});
    if (doIt) {
      await this.updService.deleteVersion(keys.keys).toPromise();
    }
    this.reload();
  }

  async updateSystemVersion(keys: GroupedKeys) {
    const requiredOs = [];
    let offline = false;

    const nodes = await this.cfgService.getNodeStates().toPromise();
    for (const nodeName of Object.keys(nodes)) {
      const state = nodes[nodeName];
      if (state.offline) {
        offline = true;
        continue;
      }
      if (!requiredOs.includes(state.config.os)) {
        requiredOs.push(state.config.os);
      }
    }

    if (offline) {
      const ok = await this.mbService.openAsync({title: 'Node Offline', message: 'At least one registered node is offline and will not be updated, continue anyway?', mode: MessageBoxMode.QUESTION});
      if (!ok) {
        return;
      }
    }

    for (const os of requiredOs) {
      if (!keys.oss.includes(os)) {
        const ok = await this.mbService.openAsync({title: 'Missing OS package', message: `At least one node requires a ${os} update, which is missing in the selected version, continue anyway?`, mode: MessageBoxMode.QUESTION});
        if (!ok) {
          return;
        }
      }
    }

    let text = `Confirm that the system should be updated to version <strong>${keys.tag}</strong>`;
    if (keys.snapshot) {
      text += '<br/><br/><strong>WARNING:</strong> You are about to install a snapshot version that is not yet released.';
    }
    const doUpdate = await this.mbService.openAsync({title: 'Confirm Update', message: text , mode: MessageBoxMode.QUESTION});
    if (!doUpdate) {
      return;
    }

    this.cfgService.stopNewVersionInterval();

    // perform update call and then wait some seconds for the master to go down.
    await this.openUpdateDialog(this.updService.updateBdeploy(keys.keys).toPromise().then(() => new Promise(r => setTimeout(r, 5000))));

    // force full reload
    window.location.reload();
  }

  private openUpdateDialog(waitFor: Promise<any>): Promise<any> {
    return this.dialog
      .open(UpdateDialogComponent, {
        minWidth: '300px',
        maxWidth: '800px',
        disableClose: true,
        data: {waitFor: waitFor, oldVersion: this.backendInfo.version },
      })
      .afterClosed().toPromise();
  }

  openUploadDialog() {
    const config = new MatDialogConfig();
    config.width = '70%';
    config.height = '75%';
    config.minWidth = '650px';
    config.minHeight = '550px';
    config.data = {
      title: 'Upload Update Packages',
      headerMessage: `Upload update packages for BDeploy or the BDeploy Client Launcher. You can provide multiple packages at once.`,
      url: this.updService.getUploadUrl(),
      fileTypes: ['.zip']
    };
    this.dialog
      .open(FileUploadComponent, config)
      .afterClosed()
      .subscribe(e => {
        this.reload();
      });
  }
}
