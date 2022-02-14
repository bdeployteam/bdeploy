import {
  animate,
  state,
  style,
  transition,
  trigger,
} from '@angular/animations';

export const scaleWidthFromZero = trigger('scaleWidthFromZero', [
  state(
    'void',
    style({
      width: 0,
      'max-width': 0,
      overflow: 'hidden',
    })
  ),
  transition('void => *', animate('0.2s 0s ease')),
]);

export const scaleWidthToZero = trigger('scaleWidthToZero', [
  state(
    'void',
    style({
      width: 0,
      'max-width': 0,
      overflow: 'hidden',
    })
  ),
  transition('* => void', animate('0.2s 0s ease')),
]);
