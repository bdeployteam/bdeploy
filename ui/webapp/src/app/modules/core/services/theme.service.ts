import { DOCUMENT } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

// keep in sync with app-theme.scss
enum Theme {
  DEFAULT = 'app-light-theme',
  LIGHT_YELLOW = 'app-light-yellow-theme',
  DARK = 'app-dark-theme',
  DARK_YELLOW = 'app-dark-yellow-theme',
}

const THEME_DESC = {};
THEME_DESC[Theme.DEFAULT] = 'Light / Blue (default)';
THEME_DESC[Theme.LIGHT_YELLOW] = 'Light / Yellow';
THEME_DESC[Theme.DARK] = 'Dark / Blue';
THEME_DESC[Theme.DARK_YELLOW] = 'Dark / Yellow';

@Injectable({
  providedIn: 'root',
})
export class ThemeService {
  activeTheme$: BehaviorSubject<Theme> = new BehaviorSubject(Theme.DEFAULT);

  constructor(@Inject(DOCUMENT) private document: Document) {
    if (localStorage.getItem('theme') === null) {
      localStorage.setItem('theme', Theme.DEFAULT);
    }
    this.updateTheme(localStorage.getItem('theme') as Theme);
  }

  public getThemes(): Theme[] {
    return Object.values(Theme);
  }

  public getThemeDescription(theme: Theme): string {
    return THEME_DESC[theme];
  }

  public getCurrentTheme(): Theme {
    return localStorage.getItem('theme') as Theme;
  }

  public updateTheme(theme: Theme) {
    localStorage.setItem('theme', theme);

    for (const v of Object.values(Theme)) {
      this.document.body.classList.remove(v);
    }

    this.document.body.classList.add(theme);

    this.activeTheme$.next(theme);
  }

  public getThemeSubject(): BehaviorSubject<Theme> {
    return this.activeTheme$;
  }

  public isDarkTheme(): boolean {
    const theme = this.getCurrentTheme();
    return theme === Theme.DARK || theme === Theme.DARK_YELLOW;
  }
}
