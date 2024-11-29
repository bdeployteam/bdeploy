import { Component, Input } from '@angular/core';
import { ParameterDescriptor } from 'src/app/models/gen.dtos';

@Component({
    selector: 'app-param-desc-card',
    templateUrl: './param-desc-card.component.html',
    styleUrls: ['./param-desc-card.component.css'],
    standalone: false
})
export class ParamDescCardComponent {
  @Input() descriptor: ParameterDescriptor;
}
