import { Component, Input } from '@angular/core';
import { ParameterDescriptor } from 'src/app/models/gen.dtos';
import { MatCard } from '@angular/material/card';

@Component({
    selector: 'app-param-desc-card',
    templateUrl: './param-desc-card.component.html',
    styleUrls: ['./param-desc-card.component.css'],
    imports: [MatCard]
})
export class ParamDescCardComponent {
  @Input() descriptor: ParameterDescriptor;
}
