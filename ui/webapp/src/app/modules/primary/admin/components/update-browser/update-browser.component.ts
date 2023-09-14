import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { SoftwareVersionBulkService } from 'src/app/modules/panels/admin/services/software-version-bulk.service';
import { SoftwareUpdateService, SoftwareVersion } from '../../services/software-update.service';

const COL_TAG: BdDataColumn<SoftwareVersion> = {
  id: 'tag',
  name: 'Version',
  data: (r) => `${r.version}${r.current ? ' - installed' : ''}`,
  classes: (r) => (r.current ? ['bd-text-bold'] : []),
};

const COL_SYSTEM: BdDataColumn<SoftwareVersion> = {
  id: 'system',
  name: 'Has System',
  data: (r) => (r.system?.length ? 'check_box' : 'check_box_outline_blank'),
  width: '50px',
  component: BdDataIconCellComponent,
};

const COL_LAUNCHER: BdDataColumn<SoftwareVersion> = {
  id: 'launcher',
  name: 'Has Launcher',
  data: (r) => (r.launcher?.length ? 'check_box' : 'check_box_outline_blank'),
  width: '50px',
  component: BdDataIconCellComponent,
};

@Component({
  selector: 'app-update-browser',
  templateUrl: './update-browser.component.html',
})
export class UpdateBrowserComponent implements OnInit {
  protected software = inject(SoftwareUpdateService);
  protected bulk = inject(SoftwareVersionBulkService);

  protected columns: BdDataColumn<SoftwareVersion>[] = [COL_TAG, COL_SYSTEM, COL_LAUNCHER];
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

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  ngOnInit() {
    this.software.load();
  }

  protected restartServer() {
    this.dialog
      .confirm(
        'Restart Server',
        'Are you sure you want to restart the server? This will interrupt existing operations and connections.'
      )
      .subscribe((x) => {
        if (!x) return;

        this.software.restartServer().subscribe();
      });
  }
}
