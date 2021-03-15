import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { UserInfo } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.css'],
})
export class SettingsComponent implements OnInit {
  /* template */ loading$ = new BehaviorSubject<boolean>(false);
  /* template */ user: UserInfo;

  constructor(private router: Router, public authService: AuthenticationService, public settings: SettingsService) {}

  ngOnInit(): void {
    this.authService
      .getUserInfo()
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((r) => {
        this.user = r;
      });
  }

  logout(): void {
    this.router.navigate(['/login']).then((result) => {
      if (result) {
        this.authService.logout();
      }
    });
  }
}
