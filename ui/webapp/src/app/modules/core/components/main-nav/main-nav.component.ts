import { AfterViewInit, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AuthenticationService } from '../../services/authentication.service';
import { GuideService } from '../../services/guide.service';
import { ThemeService } from '../../services/theme.service';
import { MainNavGuideComponent } from './main-nav-guide';

@Component({
  selector: 'app-main-nav',
  templateUrl: './main-nav.component.html',
  styleUrls: ['./main-nav.component.css'],
})
export class MainNavComponent extends MainNavGuideComponent implements OnInit, AfterViewInit {
  isAuth$: Observable<boolean> = this.authService.getTokenSubject().pipe(map((s) => s !== null));

  constructor(private authService: AuthenticationService, public themeService: ThemeService, guides: GuideService, router: Router) {
    super(guides, router);
  }

  ngOnInit(): void {}

  ngAfterViewInit(): void {
    // usually a component should provide the ngAfterViewInit directly, but we need to delay until authenticated.
    this.isAuth$.subscribe((b) => {
      if (b) {
        // delay until view has been refreshed.
        setTimeout(() => super.ngAfterViewInit());
      }
    });
  }
}
