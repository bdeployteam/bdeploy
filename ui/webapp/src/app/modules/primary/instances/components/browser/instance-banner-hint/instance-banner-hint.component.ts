import { Component, Input } from '@angular/core';
import { InstanceDto } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-instance-banner-hint',
  templateUrl: './instance-banner-hint.component.html',
  styleUrls: ['./instance-banner-hint.component.css'],
})
export class InstanceBannerHintComponent {
  @Input() record: InstanceDto;
}
