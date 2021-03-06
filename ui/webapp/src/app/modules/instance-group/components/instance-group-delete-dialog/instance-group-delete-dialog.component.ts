import { Component, Inject, OnInit } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { LoggingService } from '../../../core/services/logging.service';
import { InstanceService } from '../../../instance/services/instance.service';

export interface InstanceGroupDeleteDialogData {
  name: string;
  title: string;
  confirmation: string;
}

@Component({
  selector: 'app-instance-group-delete-dialog',
  templateUrl: './instance-group-delete-dialog.component.html',
  styleUrls: ['./instance-group-delete-dialog.component.css'],
})
export class InstanceGroupDeleteDialogComponent implements OnInit {
  private log = this.loggingService.getLogger('InstanceGroupDeleteDialogComponent');

  instanceCount = 0;

  constructor(
    public dialogRef: MatDialogRef<InstanceGroupDeleteDialogComponent>,
    private instanceService: InstanceService,
    private loggingService: LoggingService,
    @Inject(MAT_DIALOG_DATA) public data: InstanceGroupDeleteDialogData
  ) {}

  ngOnInit(): void {
    this.instanceService.listInstances(this.data.name).subscribe((list) => {
      this.instanceCount = list.length;
    });
  }

  isDeleteEnabled(): boolean {
    return this.data.name === this.data.confirmation;
  }
}
