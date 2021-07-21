import { Component, Input, OnInit } from '@angular/core';
import { UserInfo } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-user-select-option',
  templateUrl: './user-select-option.component.html',
  styleUrls: ['./user-select-option.component.css'],
})
export class UserSelectOptionComponent implements OnInit {
  @Input() option: UserInfo;

  constructor() {}

  ngOnInit(): void {}
}
