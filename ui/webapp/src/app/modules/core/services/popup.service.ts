import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class PopupService {
  private clickPopup: unknown;
  private contentAssist: unknown;

  public setClickPopup(value: unknown): void {
    this.clickPopup = value;
  }

  public hasClickPopup(): boolean {
    return !!this.clickPopup;
  }

  public setContentAssist(value: unknown): void {
    this.contentAssist = value;
  }

  public hasContentAssist(): boolean {
    return !!this.contentAssist;
  }
}
