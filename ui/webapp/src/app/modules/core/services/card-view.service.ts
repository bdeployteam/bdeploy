import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class CardViewService {
  public checkCardView(presetKey: string): boolean {
    const tempCardView = localStorage.getItem('card_view');
    if (tempCardView) {
      const tempParsedValue = JSON.parse(tempCardView);
      for (const key in tempParsedValue) {
        if (key === presetKey) {
          return tempParsedValue[key] ? tempParsedValue[key] : false;
        }
      }
    } else {
      return false;
    }
  }

  public setCardView(presetKey: string, value: boolean): void {
    const temp = localStorage.getItem('card_view');
    localStorage.setItem('card_view', JSON.stringify({ ...JSON.parse(temp), ...{ [presetKey]: value } }));
  }
}
