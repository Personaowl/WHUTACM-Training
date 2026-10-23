export function checkInPollDelay(attempt, hidden = false) {
  if (hidden) return 5000;
  if (attempt === 0) return 250;
  if (attempt < 4) return 500;
  return attempt < 10 ? 1000 : 2000;
}

export function waitForCheckInPoll(delay, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException("Aborted", "AbortError"));
      return;
    }
    const finish = () => {
      signal?.removeEventListener("abort", abort);
      resolve();
    };
    const timer = setTimeout(finish, delay);
    const abort = () => {
      clearTimeout(timer);
      signal.removeEventListener("abort", abort);
      reject(new DOMException("Aborted", "AbortError"));
    };
    signal?.addEventListener("abort", abort, { once: true });
  });
}
