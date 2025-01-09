import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { NgClass } from '@angular/common';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
    selector: 'app-instance-purpose-short',
    templateUrl: './instance-purpose-short.component.html',
    styleUrls: ['./instance-purpose-short.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgClass, MatTooltip]
})
export class InstancePurposeShortComponent {
  @Input() record: InstanceDto;

  protected getPurposeAbbrev() {
    return this.record.instanceConfiguration.purpose.charAt(0);
  }

  protected getPurposeClass(): string {
    return `local-${this.record.instanceConfiguration.purpose}`;
  }
}
