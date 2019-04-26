import { LogLevel } from '../services/logging.service';

export interface AppConfig {
  api: string;
  logLevel: LogLevel;
}
