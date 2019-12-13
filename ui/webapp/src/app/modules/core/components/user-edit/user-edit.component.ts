import { Component, ElementRef, Inject, OnInit, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material';
import { UserInfo } from 'src/app/models/gen.dtos';

@Component({
  selector: 'app-user-edit',
  templateUrl: './user-edit.component.html',
  styleUrls: ['./user-edit.component.css']
})
export class UserEditComponent implements OnInit {

  @ViewChild('pass1', { static: true })
  private pass1: ElementRef;

  @ViewChild('pass2', { static: true })
  private pass2: ElementRef;

  constructor(@Inject(MAT_DIALOG_DATA) public user: UserInfo) {
  }

  ngOnInit() {
  }

  passwordsSame() {
    if (this.user.password) {
      return this.pass1.nativeElement.value === this.pass2.nativeElement.value;
    }
    return true;
  }

}
