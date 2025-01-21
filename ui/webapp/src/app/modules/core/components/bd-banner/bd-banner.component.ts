import { Component, Input } from '@angular/core';
import { InstanceBannerRecord } from 'src/app/models/gen.dtos';
import { DatePipe } from '@angular/common';

@Component({
    selector: 'app-bd-banner',
    templateUrl: './bd-banner.component.html',
    imports: [DatePipe]
})
export class BdBannerComponent {
  @Input() banner: InstanceBannerRecord;
}
