import { Injectable } from '@angular/core';
import { Title } from '@angular/platform-browser';

@Injectable({
  providedIn: 'root',
})
export class HeaderTitleService {
  private headerTitle: string;

  constructor(private title: Title) {}

  public setHeaderTitle(title: string) {
    this.headerTitle = title;
  }

  public getHeaderTitle(): string {
    if (!this.headerTitle) {
      return this.title.getTitle();
    }

    return this.headerTitle;
  }
}
