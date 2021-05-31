import { Component, Input, OnInit } from '@angular/core';
import { PopupPosition } from 'src/app/modules/core/components/bd-popup/bd-popup.directive';
import { AllFields } from '../config-desc-cards/config-desc-cards.component';

@Component({
  selector: 'app-config-desc-element',
  templateUrl: './config-desc-element.component.html',
  styleUrls: ['./config-desc-element.component.css'],
})
export class ConfigDescElementComponent implements OnInit {
  @Input() card: AllFields;
  @Input() position: PopupPosition = 'above-right';

  constructor() {}

  ngOnInit(): void {}
}
