import { Component, Input } from '@angular/core';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
    selector: 'app-instance-banner-hint',
    templateUrl: './instance-banner-hint.component.html',
    styleUrls: ['./instance-banner-hint.component.css'],
    imports: [MatIcon, MatTooltip]
})
export class InstanceBannerHintComponent {
  @Input() record: InstanceDto;
}
