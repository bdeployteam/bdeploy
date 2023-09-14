import { Component, Input, OnInit } from '@angular/core';
import { ApplicationConfiguration, OperatingSystem } from 'src/app/models/gen.dtos';
import { getAppOs } from 'src/app/modules/core/utils/manifest.utils';

@Component({
  selector: 'app-process-name-and-os',
  templateUrl: './process-name-and-os.component.html',
})
export class ProcessNameAndOsComponent implements OnInit {
  @Input() record: ApplicationConfiguration;

  protected appOs: OperatingSystem;

  ngOnInit(): void {
    this.appOs = getAppOs(this.record.application);
  }
}
