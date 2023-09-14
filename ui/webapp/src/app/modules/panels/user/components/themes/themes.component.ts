import { Component, inject } from '@angular/core';
import { ThemeService } from 'src/app/modules/core/services/theme.service';

@Component({
  selector: 'app-themes',
  templateUrl: './themes.component.html',
  styleUrls: ['./themes.component.css'],
})
export class ThemesComponent {
  public themeService = inject(ThemeService);
}
