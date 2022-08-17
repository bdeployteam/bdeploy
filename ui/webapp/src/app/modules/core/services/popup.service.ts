import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class PopupService {
  private clickPopup: any;
  private contentAssist: any;

  public setClickPopup(value: any): void {
    this.clickPopup = value;
  }

  public hasClickPopup(): boolean {
    return !!this.clickPopup;
  }

  public setContentAssist(value: any): void {
    this.contentAssist = value;
  }

  public hasContentAssist(): boolean {
    return !!this.contentAssist;
  }
}
