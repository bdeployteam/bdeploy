import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { MinionMode } from 'src/app/models/gen.dtos';
import { ConfigService } from '../services/config.service';

@Injectable({
  providedIn: 'root',
})
export class ServerManagedGuard {
  private readonly config = inject(ConfigService);
  private readonly snackbar = inject(MatSnackBar);
  private readonly router = inject(Router);

  canActivate(route: ActivatedRouteSnapshot): boolean {
    if (this.config.config.mode !== MinionMode.MANAGED) {
      this.snackbar.open(
        `Unfortunately, ${route.url.join(
          '/',
        )} was not found (wrong URL or insufficient rights), we returned you to the safe-zone.`,
        'DISMISS',
        { panelClass: 'error-snackbar' },
      );
      this.router.navigate(['/groups/browser']);
      return false;
    }
    return true;
  }
}
