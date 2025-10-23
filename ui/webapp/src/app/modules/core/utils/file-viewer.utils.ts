import { FileEntry, TextWriter, Uint8ArrayReader, ZipReader } from '@zip.js/zip.js';
import * as Pako from 'pako';
import { Observable, catchError, forkJoin, from, map, of, switchMap } from 'rxjs';
import { RemoteDirectoryEntry, StringEntryChunkDto } from 'src/app/models/gen.dtos';

const MAX_FILE_SIZE = 1048576; // 1 MB

function isGZip(file: RemoteDirectoryEntry): boolean {
  return file.path.endsWith('.gz') || file.path.endsWith('.gzip');
}

function isZip(file: RemoteDirectoryEntry): boolean {
  return file.path.endsWith('.zip');
}

export function isArchived(file: RemoteDirectoryEntry): boolean {
  return isZip(file) || isGZip(file);
}

export function isOversized(file: RemoteDirectoryEntry): boolean {
  return file?.size > MAX_FILE_SIZE;
}

/**
 * if file is .zip or .gzip then chunk's binaryContent will be unwrapped
 * and pasted into content property.
 * Otherwise chunk will be left untouched
 */
export function unwrap(file: RemoteDirectoryEntry, chunk: StringEntryChunkDto): Observable<StringEntryChunkDto> {
  if (isZip(file)) {
    return unzip(chunk);
  } else if (isGZip(file)) {
    return ungzip(chunk);
  } else {
    return of(chunk);
  }
}

function binaryContentToUint8Array(chunk: StringEntryChunkDto): Uint8Array {
  return Uint8Array.from(atob(chunk.binaryContent), (c) => c.codePointAt(0));
}

function unzip(chunk: StringEntryChunkDto): Observable<StringEntryChunkDto> {
  return of(chunk).pipe(
    map((c) => binaryContentToUint8Array(c)),
    map((data) => new ZipReader(new Uint8ArrayReader(data))),
    switchMap((zipReader) => from(zipReader.getEntries())),
    switchMap((es) => forkJoin(es.filter(e => !e.directory).map(e => e as FileEntry).map((e) => e.getData<string>(new TextWriter())))),
    map((ss) => ss.join('\n')),
    catchError((e) => of(`failed to fetch content. ${e}`)),
    map((content) => ({ ...chunk, content }))
  );
}

function ungzip(chunk: StringEntryChunkDto): Observable<StringEntryChunkDto> {
  return of(chunk).pipe(
    map((c) => binaryContentToUint8Array(c)),
    map((data) => Pako.ungzip(data, { to: 'string' })),
    catchError((e) => of(`failed to decompress. ${e}`)),
    map((content) => ({ ...chunk, content }))
  );
}
