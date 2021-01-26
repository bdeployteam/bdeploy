import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthenticationService } from '../../services/authentication.service';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-main-nav',
  templateUrl: './main-nav.component.html',
  styleUrls: ['./main-nav.component.css'],
})
export class MainNavComponent implements OnInit {
  isAuth$: Observable<boolean> = this.authService.getTokenSubject().pipe(map((s) => s !== null));

  constructor(private authService: AuthenticationService, public themeService: ThemeService) {}

  ngOnInit(): void {}
}
