import { Component, OnInit, ViewChild } from '@angular/core';
import { InstancePurpose } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { BdPanelButtonComponent } from 'src/app/modules/core/components/bd-panel-button/bd-panel-button.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';

@Component({
  selector: 'app-edit-config',
  templateUrl: './edit-config.component.html',
  styleUrls: ['./edit-config.component.css'],
})
export class EditConfigComponent implements OnInit, DirtyableDialog {
  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild('backButton') back: BdPanelButtonComponent;

  constructor(public cfg: ConfigService, public edit: InstanceEditService) {}

  ngOnInit(): void {}

  public isDirty(): boolean {
    return this.edit.hasPendingChanges();
  }

  /* template */ getPurposes(): InstancePurpose[] {
    return [InstancePurpose.PRODUCTIVE, InstancePurpose.DEVELOPMENT, InstancePurpose.TEST];
  }

  /* template */ doApply() {
    this.edit.conceal('Change Instance Configuration');
    this.back.onClick();
  }
}
