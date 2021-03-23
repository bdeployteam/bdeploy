import { Component, Inject, InjectionToken, OnInit } from '@angular/core';
import { Version } from 'src/app/models/gen.dtos';
import { convert2String } from '../../utils/version.utils';

export interface VersionMismatch {
  oldVersion: Version;
  newVersion: Version;
}

export const VERSION_DATA = new InjectionToken<VersionMismatch>('VERSION_DATA');

@Component({
  selector: 'app-connection-version',
  templateUrl: './connection-version.component.html',
  styleUrls: ['./connection-version.component.css'],
})
export class ConnectionVersionComponent implements OnInit {
  /* template */ newVersion: string;
  /* template */ oldVersion: string;

  constructor(@Inject(VERSION_DATA) private data: VersionMismatch) {
    this.newVersion = convert2String(data.newVersion);
    this.oldVersion = convert2String(data.oldVersion);
  }

  ngOnInit(): void {}

  onReload(): void {
    window.location.reload();
  }
}
