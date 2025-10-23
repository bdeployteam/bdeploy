import { ChangeDetectionStrategy, Component, InjectionToken, inject } from '@angular/core';
import { MinionMode, Version } from 'src/app/models/gen.dtos';
import { convert2String } from '../../utils/version.utils';
import { BdLogoComponent } from '../bd-logo/bd-logo.component';
import { BdButtonComponent } from '../bd-button/bd-button.component';

export interface VersionMismatch {
  oldVersion: Version;
  newVersion: Version;
  mode: MinionMode;
}

export const VERSION_DATA = new InjectionToken<VersionMismatch>('VERSION_DATA');

@Component({
  selector: 'app-connection-version',
  templateUrl: './connection-version.component.html',
  styleUrls: ['./connection-version.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BdLogoComponent, BdButtonComponent],
})
export class ConnectionVersionComponent {
  protected newVersion: string;
  protected oldVersion: string;
  protected mode: MinionMode;

  constructor() {
    const data = inject<VersionMismatch>(VERSION_DATA);

    this.newVersion = convert2String(data.newVersion);
    this.oldVersion = convert2String(data.oldVersion);
    this.mode = data.mode;
  }

  onReload(): void {
    globalThis.location.reload();
  }
}
