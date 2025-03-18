export function timeAgo(input: number) {
  if (input <= 0) {
    return 'Never';
  }
  const date = new Date(input);
  const formatter = new Intl.RelativeTimeFormat('en');
  const ranges: Record<string, number> = {
    years: 3600 * 24 * 365,
    months: 3600 * 24 * 30,
    weeks: 3600 * 24 * 7,
    days: 3600 * 24,
    hours: 3600,
    minutes: 60,
    seconds: 1
  };
  const secondsElapsed = (date.getTime() - Date.now()) / 1000;
  for (const key in ranges) {
    if (ranges[key] < Math.abs(secondsElapsed)) {
      const delta = secondsElapsed / ranges[key];
      return formatter.format(Math.round(delta), key as Intl.RelativeTimeFormatUnit);
    }
  }
  return 'Never';
}
