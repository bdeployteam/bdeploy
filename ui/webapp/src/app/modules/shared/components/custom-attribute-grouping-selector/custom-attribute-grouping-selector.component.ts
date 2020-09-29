import { Component, EventEmitter, Input, OnChanges, Output, SimpleChange, SimpleChanges } from '@angular/core';
import { CustomAttributeDescriptor, CustomAttributesRecord } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-custom-attribute-grouping-selector',
  templateUrl: './custom-attribute-grouping-selector.component.html',
  styleUrls: ['./custom-attribute-grouping-selector.component.css']
})
export class CustomAttributeGroupingSelectorComponent implements OnChanges {

  @Input()
  label: string;

  @Input()
  sessionStorageBaseId: string;

  @Input()
  possibleAttributes: CustomAttributeDescriptor[] = [];

  @Input()
  possibleAttributesValuesMap: { [index: string]: CustomAttributesRecord } = {};

  @Output()
  attributeSelection = new EventEmitter();

  @Output()
  valuesSelection = new EventEmitter();

  get selectedAttribute(): string {
    return sessionStorage.getItem(this.sessionStorageBaseId + '_attribute');
  }
  set selectedAttribute(attribute: string) {
    if(attribute && attribute.length > 0) {
      sessionStorage.setItem(this.sessionStorageBaseId + '_attribute', attribute);
      this.attributeSelection.emit(attribute);
    } else {
      sessionStorage.removeItem(this.sessionStorageBaseId + '_attribute');
      this.attributeSelection.emit(undefined);
    }
  }

  possibleValues: string[] = [];

  get selectedValues(): string[] {
    const r = JSON.parse(sessionStorage.getItem(this.sessionStorageBaseId + '_selected'));
    return r ? r : [];
  }
  set selectedValues(values: string[]) {
    values.sort((a, b) => a ? b ? a.localeCompare(b) : -1 : 1);
    sessionStorage.setItem(this.sessionStorageBaseId + '_selected', JSON.stringify(values));
    this.valuesSelection.emit(values);
  }

  constructor() { }

  ngOnChanges(changes: SimpleChanges) {
    const changePA: SimpleChange = changes['possibleAttributes'];
    if(changePA && !changePA.firstChange) {
      // check if stored selected attribute still exists
      if (this.selectedAttribute && !this.possibleAttributes?.find(a => a.name === this.selectedAttribute)) {
        this.updateAttributeSelection(undefined);
      } else {
        this.attributeSelection.emit(this.selectedAttribute);
      }
    }
    const changePAV: SimpleChange = changes['possibleAttributesValuesMap'];
    if(changePAV && !changePAV.firstChange) {
      this.updatePossibleAttributeValues();
      // remove disappeared values from selection
      this.selectedValues = this.selectedValues.filter(a => this.possibleValues.find(v => a == v) !== undefined);
    }
  }

  updateAttributeSelection(attribute: string) {
    this.selectedAttribute = attribute;
    this.updatePossibleAttributeValues();
    this.selectAllValues();
  }

  private updatePossibleAttributeValues() {
    this.possibleValues = [];
    if (this.selectedAttribute) {
      const values = Object.values(this.possibleAttributesValuesMap).map(ca => {
        const v = ca.attributes[this.selectedAttribute];
        return v ? v : null;
      });
      this.possibleValues = Array.from(new Set(values)).sort();
    }
  }

  isValueSelected(value: string): boolean {
    return this.selectedValues?.indexOf(value) >= 0;
  }

  selectAllValues() {
    this.selectedValues = this.possibleValues;
  }

  deselectAllValues() {
    this.selectedValues = [];
  }

  toggleValueSelection(value: string) {
    const arr = this.selectedValues;
    const idx = arr.indexOf(value);
    if (idx >= 0) {
      arr.splice(idx, 1);
    } else {
      arr.push(value);
    }
    this.selectedValues = arr;
  }

}
