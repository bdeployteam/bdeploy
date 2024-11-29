import { Component, HostBinding, Input, OnChanges, OnInit } from '@angular/core';
import { ApplicationConfiguration, ApplicationStartType } from 'src/app/models/gen.dtos';

@Component({
    selector: 'app-process-starttype-icon',
    templateUrl: './process-starttype-icon.component.html',
    standalone: false
})
export class ProcessStarttypeIconComponent implements OnInit, OnChanges {
  @Input() record: ApplicationConfiguration;
  @HostBinding('attr.data-cy') dataCy: string;

  protected data: {
    icon: string;
    hint: string;
  };

  ngOnInit(): void {
    this.ngOnChanges();
  }

  ngOnChanges(): void {
    const state = this.record.processControl?.startType;
    this.dataCy = state;

    switch (state) {
      case ApplicationStartType.INSTANCE:
        this.data = { icon: 'conveyor_belt', hint: 'Automatic with instance start' };
        break;
      case ApplicationStartType.MANUAL:
        this.data = { icon: 'person', hint: 'Manually by user' };
        break;
      case ApplicationStartType.MANUAL_CONFIRM:
        this.data = { icon: 'person_edit', hint: 'Manually by user with additional confirmation' };
        break;
    }
  }
}
