import { Injectable } from '@angular/core';
import { Title } from '@angular/platform-browser';

@Injectable({
  providedIn: 'root'
})
export class HeaderTitleService {

  private headerTitle: String;

  constructor(private title: Title) { }

  public setHeaderTitle(title: String) {
    this.headerTitle = title;
  }

  public getHeaderTitle(): String {
    if (!this.headerTitle) {
      return this.title.getTitle();
    }

    return this.headerTitle;
  }
}
