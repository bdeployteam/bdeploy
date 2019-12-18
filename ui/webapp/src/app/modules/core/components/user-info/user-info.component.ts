import { Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material';
import { Router } from '@angular/router';
import { cloneDeep } from 'lodash';
import { UserInfo } from 'src/app/models/gen.dtos';
import { AuthenticationService } from '../../services/authentication.service';
import { SettingsService } from '../../services/settings.service';
import { UserEditComponent } from '../user-edit/user-edit.component';

@Component({
  selector: 'app-user-info',
  templateUrl: './user-info.component.html',
  styleUrls: ['./user-info.component.css'],
  providers: [SettingsService]
})
export class UserInfoComponent implements OnInit {

  public user: UserInfo;

  constructor(private router: Router, private authService: AuthenticationService, public settings: SettingsService, private dialog: MatDialog) { }

  ngOnInit() {
    this.authService.getUserInfo().subscribe(r => {
      this.user = r;
    });
  }

  logout(): void {
    this.router.navigate(['/login']).then(result => {
      if (result) {
        this.authService.logout();
      }
    });
  }

  edit() {
    this.dialog.open(UserEditComponent, {
      width: '500px',
      data: cloneDeep(this.user),
    }).afterClosed().subscribe(r => {
      if (r) {
        this.authService.updateUserInfo(r).subscribe(_ => {
          this.user = r;
          this.user.password = null;
        });
      }
    });
  }

}
