
import { Injectable, inject, DOCUMENT } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

// keep in sync with app-theme.scss
enum Theme {
  DEFAULT = 'app-light-theme',
  DARK = 'app-dark-theme',
}

const THEME_DESC: Record<string, string> = {};
THEME_DESC[Theme.DEFAULT] = 'Light Theme (default)';
THEME_DESC[Theme.DARK] = 'Dark Theme';

@Injectable({
  providedIn: 'root',
})
export class ThemeService {
  private readonly document = inject<Document>(DOCUMENT);

  activeTheme$: BehaviorSubject<Theme> = new BehaviorSubject<Theme>(Theme.DEFAULT);

  constructor() {
    const themeName = localStorage.getItem('theme');
    if (!themeName || (themeName !== Theme.DARK && themeName !== Theme.DEFAULT)) {
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
    return theme === Theme.DARK;
  }
}
