import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';

@Component({
  selector: 'app-custom-property-value',
  templateUrl: './custom-property-value.component.html',
  styleUrls: ['./custom-property-value.component.css']
})
export class CustomPropertyValueComponent implements OnInit {

  isCreate = false;

  constructor(@Inject(MAT_DIALOG_DATA) public data: any) { }

  ngOnInit() {
    this.isCreate = !this.data.propertyName;
    if (this.isCreate) {
      this.data.propertyName = '';
      this.data.propertyValue = '';
    }
  }

  getDescription(): string {
    const descriptor = this.data.descriptors.find(d => d.name === this.data.propertyName);
    return descriptor ? descriptor.description : undefined;
  }

  getResult(): Object {
    return {
      name: this.data.propertyName,
      value: this.data.propertyValue,
    }
  }
}
