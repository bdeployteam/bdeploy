import { Component, OnInit } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import {
  SoftwareUpdateService,
  SoftwareVersion,
} from '../../services/software-update.service';

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
  styleUrls: ['./update-browser.component.css'],
})
export class UpdateBrowserComponent implements OnInit {
  /* template */ columns: BdDataColumn<SoftwareVersion>[] = [
    COL_TAG,
    COL_SYSTEM,
    COL_LAUNCHER,
  ];
  /* template */ getRecordRoute = (r: SoftwareVersion) => {
    return [
      '',
      {
        outlets: {
          panel: ['panels', 'admin', 'software', 'details', r.version],
        },
      },
    ];
  };

  constructor(public software: SoftwareUpdateService) {}

  ngOnInit() {
    this.software.load();
  }
}
