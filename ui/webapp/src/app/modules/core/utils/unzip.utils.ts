import { Observable, of } from 'rxjs';
import { StringEntryChunkDto } from 'src/app/models/gen.dtos';

export function unwrap(
  chunk: StringEntryChunkDto
): Observable<StringEntryChunkDto> {
  return of(chunk);
}
