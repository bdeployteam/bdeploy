import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { cloneDeep } from 'lodash';
import { EMPTY_INSTANCE_BANNER_RECORD } from 'src/app/models/consts';

@Component({
  selector: 'app-instance-banner-edit',
  templateUrl: './instance-banner-edit.component.html',
  styleUrls: ['./instance-banner-edit.component.css']
})
export class InstanceBannerEditComponent implements OnInit {

  private DEFAULT_FOREGROUND = '#ffffff';
  private DEFAULT_BACKGROUND = '#ff4444';

  public bannerFormGroup = this.fb.group({
    foregroundColor: [''],
    backgroundColor: [''],
    text: ['', Validators.required],
  });

  get foregroundColorControl() {
    return this.bannerFormGroup.get('foregroundColor');
  }

  get backgroundColorControl() {
    return this.bannerFormGroup.get('backgroundColor');
  }

  get textControl() {
    return this.bannerFormGroup.get('text');
  }

  constructor(
    private fb: FormBuilder,
    @Inject(MAT_DIALOG_DATA) public data: any) {
  }

  ngOnInit() {
    this.bannerFormGroup.setValue(this.data.instanceBanner);
    if (!this.canRemove()) {
      this.foregroundColorControl.setValue(this.DEFAULT_FOREGROUND);
      this.backgroundColorControl.setValue(this.DEFAULT_BACKGROUND);
    }
  }

  canRemove() : boolean {
    return this.data.instanceBanner && this.data.instanceBanner.text != null;
  }

  canSet() : boolean {
    const s = this.textControl.value;
    return s != null && s.trim().length > 0;
  }

  getResult4Remove() {
    return cloneDeep(EMPTY_INSTANCE_BANNER_RECORD);
  }

  getResult() {
    return this.bannerFormGroup.getRawValue();
  }
}
