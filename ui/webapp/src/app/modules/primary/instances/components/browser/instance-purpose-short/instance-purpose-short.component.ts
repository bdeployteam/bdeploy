import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { InstanceDto } from 'src/app/models/gen.dtos';

@Component({
    selector: 'app-instance-purpose-short',
    templateUrl: './instance-purpose-short.component.html',
    styleUrls: ['./instance-purpose-short.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false
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
