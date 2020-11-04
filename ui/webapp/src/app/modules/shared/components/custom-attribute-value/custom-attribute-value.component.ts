import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';

@Component({
  selector: 'app-custom-attribute-value',
  templateUrl: './custom-attribute-value.component.html',
  styleUrls: ['./custom-attribute-value.component.css'],
})
export class CustomAttributeValueComponent implements OnInit {
  isCreate = false;

  constructor(@Inject(MAT_DIALOG_DATA) public data: any) {}

  ngOnInit() {
    this.isCreate = !this.data.attributeName;
    if (this.isCreate) {
      this.data.attributeName = '';
      this.data.attributeValue = '';
    }
  }

  getDescription(): string {
    const descriptor = this.data.descriptors.find((d) => d.name === this.data.attributeName);
    return descriptor ? descriptor.description : undefined;
  }

  getResult(): Object {
    return {
      name: this.data.attributeName,
      value: this.data.attributeValue,
    };
  }
}
