import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { ThemesComponent } from './components/themes/themes.component';
import { UserRoutingModule } from './user-routing.module';

@NgModule({
  declarations: [ThemesComponent],
  imports: [CommonModule, CoreModule, UserRoutingModule],
})
export class UserModule {}
