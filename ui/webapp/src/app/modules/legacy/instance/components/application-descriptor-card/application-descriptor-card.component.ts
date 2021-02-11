import { Component, Input, OnInit } from '@angular/core';
import { ApplicationGroup } from '../../../../../models/application.model';

@Component({
  selector: 'app-application-descriptor-card',
  templateUrl: './application-descriptor-card.component.html',
  styleUrls: ['./application-descriptor-card.component.css'],
})
export class ApplicationDescriptorCardComponent implements OnInit {
  @Input() applicationGroup: ApplicationGroup;

  constructor() {}

  ngOnInit() {}
}
