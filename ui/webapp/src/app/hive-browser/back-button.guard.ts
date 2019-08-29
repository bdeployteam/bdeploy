import { Injectable } from '@angular/core';
import { CanDeactivate } from '@angular/router';
import { HiveService } from '../services/hive.service';

@Injectable({
  providedIn: 'root',
})
export class BackButtonGuard implements CanDeactivate<any> {

  constructor(private hiveService: HiveService) {}

  canDeactivate(component: any) {
    if (this.hiveService.getBackClicked()) {
      this.hiveService.setBackClicked(false);
      history.pushState(null, null, location.href);
      return false;
    }
    return true;
  }
}
