import { Component, OnInit, ViewChild } from '@angular/core';
import { MatToolbar } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthenticationService } from '../services/authentication.service';
import { ConfigService } from '../services/config.service';
import { ThemeService } from '../services/theme.service';


@Component({
  selector: 'app-main-nav',
  templateUrl: './main-nav.component.html',
  styleUrls: ['./main-nav.component.css'],
})
export class MainNavComponent implements OnInit {

  @ViewChild('mainToolbar') mainTb: MatToolbar;

  isAuth$: Observable<boolean> = this.authService.getTokenSubject().pipe(map(s => s !== null));

  backendVersion: BehaviorSubject<string> = new BehaviorSubject('No connection');

  constructor(
    private authService: AuthenticationService,
    private cfgService: ConfigService,
    private router: Router,
    public theme: ThemeService,
    public title: Title,
  ) {}

  ngOnInit(): void {
    this.authService.getTokenSubject().subscribe(v => {
      if (this.authService.isAuthenticated()) {
        this.cfgService.getBackendVersion().subscribe(ver => {
          this.backendVersion.next(ver);
        });
      }
    });
  }

  logout(): void {
    this.router.navigate(['/login']).then(result => {
      if (result) {
        this.authService.logout();
      }
    });
  }

}
