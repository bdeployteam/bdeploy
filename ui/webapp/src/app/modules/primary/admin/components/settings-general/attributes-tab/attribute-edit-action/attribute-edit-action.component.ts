import { Component, Input, OnInit } from '@angular/core';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';

@Component({
  selector: 'attribute-edit-action',
  templateUrl: './attribute-edit-action.component.html',
  styleUrls: ['./attribute-edit-action.component.css'],
})
export class AttributeEditActionComponent implements OnInit {
  @Input() record: CustomAttributeDescriptor;

  constructor() {}

  ngOnInit(): void {}
}
