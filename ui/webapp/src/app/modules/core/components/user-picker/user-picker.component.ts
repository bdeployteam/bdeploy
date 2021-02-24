import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormControl } from '@angular/forms';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Observable } from 'rxjs';
import { map, startWith } from 'rxjs/operators';
import { UserInfo } from 'src/app/models/gen.dtos';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
  selector: 'app-user-picker',
  templateUrl: './user-picker.component.html',
  styleUrls: ['./user-picker.component.css'],
})
export class UserPickerComponent implements OnInit {
  private static MAX_OPTIONS = 100;

  public userControl = new FormControl();
  public filteredUsers: Observable<UserInfo[]>;
  public filteredUsersShortened: Observable<boolean>;

  constructor(@Inject(MAT_DIALOG_DATA) public data: any, private fb: FormBuilder, public settings: SettingsService) {
    this.filteredUsers = this.userControl.valueChanges.pipe(
      startWith(''),
      map((input) => this.filter(input)),
      map((users) => users.slice(0, UserPickerComponent.MAX_OPTIONS))
    );

    this.filteredUsersShortened = this.userControl.valueChanges.pipe(
      startWith(false),
      map((input) => this.filter(input)),
      map((users) => users.length > UserPickerComponent.MAX_OPTIONS)
    );
  }

  ngOnInit() {}

  private filter(input: string): UserInfo[] {
    const inputLC = input ? input.toLowerCase() : '';
    return this.data.all.filter(
      (u) =>
        (u.name.toLowerCase().indexOf(inputLC) >= 0 ||
          (u.fullName && u.fullName.toLowerCase().indexOf(inputLC) >= 0)) &&
        this.data.displayed.find((d) => d.name === u.name) === undefined
    );
  }

  public isValid(): boolean {
    return (
      this.userControl.value !== null && this.userControl.value.trim().length > 0 && this.getResult() !== undefined
    );
  }

  public getResult(): UserInfo {
    const input = this.userControl.value ? this.userControl.value.toLowerCase() : '';

    return this.data.all.find(
      (u) => u.name.toLowerCase() === input && this.data.displayed.find((d) => d.name === u.name) === undefined
    );
  }
}
