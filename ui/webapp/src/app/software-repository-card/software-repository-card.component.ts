import { Component, Input, OnInit } from '@angular/core';
import { SoftwareRepositoryConfiguration } from '../models/gen.dtos';

@Component({
  selector: 'app-software-repository-card',
  templateUrl: './software-repository-card.component.html',
  styleUrls: ['./software-repository-card.component.css']
})
export class SoftwareRepositoryCardComponent implements OnInit {

  @Input() repository: SoftwareRepositoryConfiguration = null;

  constructor() { }

  ngOnInit() {
  }

}
