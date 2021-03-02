import { animate, animateChild, group, query, style, transition, trigger } from '@angular/animations';

export const routerAnimation = trigger('routerAnimation', [
  transition('* <=> *', [
    // Set a default  style for enter and leave
    group([
      query(
        ':enter',
        [
          style({
            position: 'absolute',
            left: 0,
            width: '100%',
            height: '100%',
            opacity: 0,
            transform: 'scale(1.05)',
          }),
          group([animate('200ms ease', style({ opacity: 1, transform: 'scale(1)' })), animateChild()]),
        ],
        { optional: true }
      ),
      query(
        ':leave',
        [
          style({
            position: 'absolute',
            left: 0,
            width: '100%',
            height: '100%',
            opacity: 1,
            transform: 'scale(1)',
          }),
          group([animate('150ms ease', style({ opacity: 0, transform: 'scale(0.95)' })), animateChild()]),
        ],
        { optional: true }
      ),
    ]),
  ]),
]);
