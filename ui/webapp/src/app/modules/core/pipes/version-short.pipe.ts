import { Pipe, PipeTransform } from '@angular/core';
import { Version } from 'src/app/models/gen.dtos';

@Pipe({
  name: 'formatVersionShort',
})
export class VersionShortPipe implements PipeTransform {
  transform(value: Version): string {
    return `${value.major}.${value.minor}.${value.micro}${
      !value.qualifier?.length ? '' : 'S'
    }`;
  }
}
