import { Component, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from '../../../core/services/settings.service';

@Component({
  selector: 'app-settings-general',
  templateUrl: './settings-general.component.html',
  styleUrls: ['./settings-general.component.css'],
  encapsulation: ViewEncapsulation.None,
})
export class SettingsGeneralComponent implements OnInit, DirtyableDialog {
  /* template */ addPlugin$ = new Subject<any>();

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;

  constructor(public settings: SettingsService, areas: NavAreasService) {
    areas.registerDirtyable(this, 'admin');
  }

  ngOnInit() {}

  public isDirty(): boolean {
    return this.settings.isDirty();
  }

  public doSave(): Observable<any> {
    return this.settings.save();
  }
}
