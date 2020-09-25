import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-custom-attribute-edit',
  templateUrl: './custom-attribute-edit.component.html',
  styleUrls: ['./custom-attribute-edit.component.css']
})
export class CustomAttributeEditComponent implements OnInit {

  constructor(@Inject(MAT_DIALOG_DATA) public attribute: CustomAttributeDescriptor) {
  }

  ngOnInit() {
    if (!this.attribute) {
      this.attribute = <CustomAttributeDescriptor>{};
    }
  }

}
