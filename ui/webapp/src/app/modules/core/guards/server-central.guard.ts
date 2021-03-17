import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { MinionMode } from 'src/app/models/gen.dtos';
import { ConfigService } from '../services/config.service';

@Injectable({
  providedIn: 'root',
})
export class ServerCentralGuard implements CanActivate {
  constructor(private config: ConfigService, private snackbar: MatSnackBar, private router: Router) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    if (this.config.config.mode !== MinionMode.CENTRAL) {
      this.snackbar.open(
        `Unfortunately, ${route.url.join('/')} was not found (wrong URL or insufficient rights), we returned you to the safe-zone.`,
        'DISMISS',
        { panelClass: 'error-snackbar' }
      );
      this.router.navigate(['/groups/browser']);
      return false;
    }
    return true;
  }
}
