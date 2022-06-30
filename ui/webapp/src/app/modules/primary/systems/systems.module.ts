import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { SystemBrowserComponent } from './components/system-browser/system-browser.component';
import { SystemsRoutingModule } from './systems-routing.module';

@NgModule({
  declarations: [SystemBrowserComponent],
  imports: [CommonModule, SystemsRoutingModule, CoreModule],
})
export class SystemsModule {}
