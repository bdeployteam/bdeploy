import { Component } from '@angular/core';
import { BdLogoComponent } from '../bd-logo/bd-logo.component';
import { MatProgressSpinner } from '@angular/material/progress-spinner';

@Component({
    selector: 'app-connection-lost',
    templateUrl: './connection-lost.component.html',
    styleUrls: ['./connection-lost.component.css'],
    imports: [BdLogoComponent, MatProgressSpinner]
})
export class ConnectionLostComponent {}
