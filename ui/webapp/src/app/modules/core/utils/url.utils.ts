/**
 * "Something URL-like", which can be split into scheme, host, port and "the rest".
 *
 * Each of the individual parts can be modified before using toString to get back out a "similar URL-like thing".
 */
export class URLish {
  public scheme: string;
  public hostname: string;
  public port: string;
  public pathAndQuery: string;

  /**
   * Groups matched by this regular expression:
   * 0: all
   * 1: scheme including ://
   * 2: host with port if present
   * 3: host if port is present
   * 4: port with leading : if port is present
   * 5: port if port is present
   * 6: host if port is *not* present
   * 7: host if port is *not* present and a trailing path *is* present
   * 8: host if port is *not* present and a trailing path is *not* present
   * 9: trailing path if present
   *
   * We simply have to use groups 1, 3, 5, 6 and 9 :)
   */
  private regex = /(\S+:\/\/)?((.+(?=:\d))(:(\d+))|((.+(?=\/))|(.+)))?(\/.*)?/;

  constructor(url: string) {
    const result = this.regex.exec(url);

    this.scheme = result[1];
    this.hostname = result[3] ? result[3] : result[6]; // either with or without port matched
    this.port = result[5];
    this.pathAndQuery = result[9];
  }

  public toString() {
    return `${this.scheme ? this.scheme : ''}${this.hostname}${
      this.port ? ':' + this.port : ''
    }${this.pathAndQuery ? this.pathAndQuery : ''}`;
  }
}
