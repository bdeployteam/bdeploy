import { Component, OnInit, ViewChild } from '@angular/core';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { ProcessEditService } from '../../../services/process-edit.service';

@Component({
  selector: 'app-configure-endpoints',
  templateUrl: './configure-endpoints.component.html',
  styleUrls: ['./configure-endpoints.component.css'],
})
export class ConfigureEndpointsComponent implements OnInit, DirtyableDialog {
  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;

  constructor(public edit: ProcessEditService) {}

  ngOnInit(): void {}

  isDirty(): boolean {
    return false; // TODO;
  }
}
