import { Pipe, PipeTransform } from '@angular/core';
import { Version } from 'src/app/models/gen.dtos';
import { convert2String } from '../../legacy/shared/utils/version.utils';

@Pipe({
  name: 'formatVersion',
})
export class VersionPipe implements PipeTransform {
  transform(value: Version): unknown {
    return convert2String(value);
  }
}
