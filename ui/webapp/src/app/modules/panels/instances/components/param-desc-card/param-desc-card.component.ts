import { Component, Input, OnInit } from '@angular/core';
import { ParameterDescriptor } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-param-desc-card',
  templateUrl: './param-desc-card.component.html',
  styleUrls: ['./param-desc-card.component.css'],
})
export class ParamDescCardComponent implements OnInit {
  @Input() descriptor: ParameterDescriptor;

  constructor() {}

  ngOnInit(): void {}
}
