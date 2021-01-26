import { animate, query, style, transition, trigger } from '@angular/animations';

export const routerAnimation = trigger('routerAnimation', [
  transition('* <=> *', [
    // Set a default  style for enter and leave
    query(
      ':enter, :leave',
      [
        style({
          position: 'absolute',
          left: 0,
          width: '100%',
          height: '100%',
          opacity: 0,
          transform: 'scale(1.05)',
        }),
      ],
      { optional: true }
    ),
    // Animate the new page in
    query(':enter', [animate('200ms ease', style({ opacity: 1, transform: 'scale(1)' }))], { optional: true }),
  ]),
]);
