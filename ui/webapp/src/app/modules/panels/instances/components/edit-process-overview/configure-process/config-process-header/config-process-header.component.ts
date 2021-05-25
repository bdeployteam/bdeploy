import { Component, OnInit, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { ApplicationDto, ApplicationStartType } from 'src/app/models/gen.dtos';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProcessEditService } from '../../../../services/process-edit.service';

@Component({
  selector: 'app-config-process-header',
  templateUrl: './config-process-header.component.html',
  styleUrls: ['./config-process-header.component.css'],
})
export class ConfigProcessHeaderComponent implements OnInit {
  @ViewChild('form') private form: NgForm;

  constructor(public edit: ProcessEditService, public instanceEdit: InstanceEditService) {}

  ngOnInit(): void {}

  public isInvalid(): boolean {
    return this.form.invalid;
  }

  public getStartTypes(app: ApplicationDto): ApplicationStartType[] {
    const supported = app?.descriptor?.processControl?.supportedStartTypes;
    if (!supported?.length || !!supported.find((s) => s === ApplicationStartType.INSTANCE)) {
      return [ApplicationStartType.INSTANCE, ApplicationStartType.MANUAL, ApplicationStartType.MANUAL_CONFIRM];
    } else if (!!supported.find((s) => s === ApplicationStartType.MANUAL)) {
      return [ApplicationStartType.MANUAL, ApplicationStartType.MANUAL_CONFIRM];
    } else {
      return supported;
    }
  }

  public getStartTypeLabels(app: ApplicationDto): string[] {
    return this.getStartTypes(app).map((t) => {
      switch (t) {
        case ApplicationStartType.INSTANCE:
          return 'Instance (Automatic)';
        case ApplicationStartType.MANUAL:
          return 'Manual';
        case ApplicationStartType.MANUAL_CONFIRM:
          return 'Manual (with confirmation)';
      }
    });
  }
}
