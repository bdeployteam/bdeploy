import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { CustomPropertyDescriptor } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-custom-property-edit',
  templateUrl: './custom-property-edit.component.html',
  styleUrls: ['./custom-property-edit.component.css']
})
export class CustomPropertyEditComponent implements OnInit {

  constructor(@Inject(MAT_DIALOG_DATA) public property: CustomPropertyDescriptor) {
  }

  ngOnInit() {
    if (!this.property) {
      this.property = <CustomPropertyDescriptor>{};
    }
  }

}
