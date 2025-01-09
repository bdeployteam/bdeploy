import { Component, inject, OnInit, ViewChild } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { SoftwareVersionBulkService } from 'src/app/modules/panels/admin/services/software-version-bulk.service';
import { SoftwareUpdateService, SoftwareVersion } from '../../services/software-update.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';
import { AsyncPipe } from '@angular/common';

const colTag: BdDataColumn<SoftwareVersion> = {
  id: 'tag',
  name: 'Version',
  data: (r) => `${r.version}${r.current ? ' - installed' : ''}`,
  classes: (r) => (r.current ? ['bd-text-bold'] : []),
};

const colSystem: BdDataColumn<SoftwareVersion> = {
  id: 'system',
  name: 'Has System',
  data: (r) => (r.system?.length ? 'check_box' : 'check_box_outline_blank'),
  width: '50px',
  component: BdDataIconCellComponent,
};

const colLauncher: BdDataColumn<SoftwareVersion> = {
  id: 'launcher',
  name: 'Has Launcher',
  data: (r) => (r.launcher?.length ? 'check_box' : 'check_box_outline_blank'),
  width: '50px',
  component: BdDataIconCellComponent,
};

@Component({
    selector: 'app-update-browser',
    templateUrl: './update-browser.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdPanelButtonComponent, BdButtonComponent, BdDialogContentComponent, BdDataTableComponent, AsyncPipe]
})
export class UpdateBrowserComponent implements OnInit {
  protected readonly software = inject(SoftwareUpdateService);
  protected readonly bulk = inject(SoftwareVersionBulkService);

  protected readonly columns: BdDataColumn<SoftwareVersion>[] = [colTag, colSystem, colLauncher];
  protected getRecordRoute = (r: SoftwareVersion) => {
    return [
      '',
      {
        outlets: {
          panel: ['panels', 'admin', 'software', 'details', r.version],
        },
      },
    ];
  };

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  ngOnInit() {
    this.software.load();
  }

  protected restartServer() {
    this.dialog
      .confirm(
        'Restart Server',
        'Are you sure you want to restart the server? This will interrupt existing operations and connections.',
      )
      .subscribe((x) => {
        if (!x) return;

        this.software.restartServer().subscribe();
      });
  }

  protected createStackDump() {
    this.software.createStackDump().subscribe(() => {
      this.dialog
        .info('Stack Dump', 'The stack dump was created and can be found on the server logging page', 'monitoring')
        .subscribe();
    });
  }
}
