import { Component, OnInit, TemplateRef } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { cloneDeep } from 'lodash-es';
import { UserInfo } from 'src/app/models/gen.dtos';
import { AuthenticationService } from '../../services/authentication.service';
import { Logger, LoggingService } from '../../services/logging.service';
import { SettingsService } from '../../services/settings.service';
import { UserEditComponent } from '../user-edit/user-edit.component';
import { UserPasswordComponent } from '../user-password/user-password.component';

@Component({
  selector: 'app-user-info',
  templateUrl: './user-info.component.html',
  styleUrls: ['./user-info.component.css'],
  providers: [SettingsService]
})
export class UserInfoComponent implements OnInit {

  private readonly log: Logger = this.loggingService.getLogger('UserInfoComponent');

  public user: UserInfo;
  public pack: String;
  public genFull = false;

  private dialogRef: MatDialogRef<any>;

  constructor(private loggingService: LoggingService,
    private router: Router,
    private authService: AuthenticationService,
    public settings: SettingsService,
    private dialog: MatDialog,
    private snackbarService: MatSnackBar) { }

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
      data: {
        isCreate: false,
        user: cloneDeep(this.user)
      }
    }).afterClosed().subscribe(r => {
      if (r) {
        this.authService.updateUserInfo(r).subscribe(_ => {
          this.user = r;
          this.user.password = null;
        });
      }
    });
  }

  changePassword() {
    this.dialog.open(UserPasswordComponent, {
      width: '500px',
      data: {
        isAdmin: false,
        user: this.user.name
      },
    }).afterClosed().subscribe(r => {
      if (r) {
        this.authService.changePassword(r).subscribe(
          result => {
            this.log.info('user ' + this.user.name + 'successfully changed password');
          },
          error => {
            if (error.status === 401) {
              this.log.warn('user ' + this.user.name + ': wrong password!');
              this.changePassword();
            } else {
              throw(error);
            }
          }
        );
      }
    });
  }

  copied() {
    this.snackbarService.open('Token copied to clipboard.', null, { duration: 2000 });
    this.dialogRef.close();
  }

  async openDialog(ref: TemplateRef<unknown>) {
    this.regenPack();
    this.dialogRef = this.dialog.open(ref, {
      width: '600px'
    });
  }

  regenPack() {
    this.authService.getAuthPackForUser(this.genFull).subscribe(r => this.pack = r);
  }

  public getTitleUserName(): string {
    if (this.user) {
      return this.user.fullName ? this.user.fullName : this.user.name;
    }
    return '--';
  }

}
