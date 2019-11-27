import { HttpHeaders } from '@angular/common/http';
import { NO_ERROR_HANDLING_HDR } from '../../../models/consts';

export function suppressGlobalErrorHandling(p: HttpHeaders): HttpHeaders {
  return p.append(NO_ERROR_HANDLING_HDR, 'true');
}

