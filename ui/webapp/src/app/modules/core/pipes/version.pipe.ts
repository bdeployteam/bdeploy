import { Pipe, PipeTransform } from '@angular/core';
import { Version } from 'src/app/models/gen.dtos';
import { convert2String } from '../utils/version.utils';

@Pipe({ name: 'formatVersion' })
export class VersionPipe implements PipeTransform {
  transform(value: Version): string {
    return convert2String(value);
  }
}
