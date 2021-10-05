import { animate, style, transition, trigger } from '@angular/animations';

export const easeX = trigger('easeX', [
  transition(':enter', [
    style({ transform: 'translateX({{transEnter}})' }),
    animate('0.15s {{delayEnter}} ease', style({ transform: 'translateX(0%)' })),
  ]),
  transition(':leave', [animate('0.15s {{delayLeave}} ease', style({ transform: 'translateX({{transLeave}})' }))]),
]);
