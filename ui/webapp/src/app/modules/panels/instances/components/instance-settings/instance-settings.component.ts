import { Component, OnInit, ViewChild } from '@angular/core';
import { InstancePurpose } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';

@Component({
  selector: 'app-instance-settings',
  templateUrl: './instance-settings.component.html',
  styleUrls: ['./instance-settings.component.css'],
})
export class InstanceSettingsComponent implements OnInit, DirtyableDialog {
  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;

  constructor(public cfg: ConfigService, public edit: InstanceEditService, private areas: NavAreasService) {}

  ngOnInit(): void {}

  public isDirty(): boolean {
    return this.edit.hasPendingChanges();
  }

  /* template */ getPurposes(): InstancePurpose[] {
    return [InstancePurpose.PRODUCTIVE, InstancePurpose.DEVELOPMENT, InstancePurpose.TEST];
  }

  /* template */ doApply() {
    this.edit.conceal('Change Instance Configuration');
    this.areas.closePanel();
  }
}
