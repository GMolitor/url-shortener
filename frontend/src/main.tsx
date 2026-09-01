import { FormEvent, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

type LinkResponse = {
  code: string;
  shortUrl: string;
  destinationUrl: string;
  createdAt: string;
};

type ApiError = { message?: string };

const MAX_URL_LENGTH = 2048;

function validateUrl(value: string): string | null {
  if (value.length === 0 || value.length > MAX_URL_LENGTH) {
    return 'Enter a URL between 1 and 2048 characters.';
  }
  if (
    [...value].some((character) => {
      const code = character.charCodeAt(0);
      return code < 0x20 || code === 0x7f;
    })
  ) {
    return 'The URL cannot contain control characters.';
  }

  try {
    const parsedUrl = new URL(value);
    if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
      return 'Use an HTTP or HTTPS URL.';
    }
    if (parsedUrl.username || parsedUrl.password) {
      return 'URLs with embedded credentials are not allowed.';
    }
  } catch {
    return 'Enter a valid URL, such as https://example.com.';
  }
  return null;
}

export function App() {
  const [url, setUrl] = useState('');
  const [result, setResult] = useState<LinkResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>(
    'idle',
  );

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedUrl = url.trim();
    const validationError = validateUrl(trimmedUrl);
    if (validationError) {
      setError(validationError);
      setResult(null);
      return;
    }

    setIsSubmitting(true);
    setError(null);
    setResult(null);
    setCopyState('idle');
    try {
      const response = await fetch('/api/links', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: trimmedUrl }),
      });
      const payload = (await response.json().catch(() => ({}))) as
        | LinkResponse
        | ApiError;
      if (!response.ok) {
        throw new Error(
          ('message' in payload && payload.message) ||
            'The link could not be created. Try again.',
        );
      }
      setResult(payload as LinkResponse);
    } catch (submissionError) {
      setError(
        submissionError instanceof Error
          ? submissionError.message
          : 'The link could not be created. Try again.',
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  async function copyShortUrl() {
    if (!result) return;
    try {
      await navigator.clipboard.writeText(result.shortUrl);
      setCopyState('copied');
    } catch {
      setCopyState('failed');
    }
  }

  return (
    <main className="page-shell">
      <section className="card" aria-labelledby="page-title">
        <p className="eyebrow">Simple links, ready to share</p>
        <h1 id="page-title">Shorten a URL</h1>
        <p className="intro">
          Create a compact link for any HTTP or HTTPS destination.
        </p>

        <form onSubmit={submit} noValidate>
          <label htmlFor="destination-url">Destination URL</label>
          <div className="input-row">
            <input
              id="destination-url"
              name="url"
              type="url"
              value={url}
              onChange={(event) => setUrl(event.target.value)}
              placeholder="https://example.com/article"
              maxLength={MAX_URL_LENGTH}
              aria-describedby="url-help"
              required
              disabled={isSubmitting}
            />
            <button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Creating…' : 'Shorten URL'}
            </button>
          </div>
          <p id="url-help" className="field-help">
            HTTP or HTTPS only · maximum 2048 characters
          </p>
        </form>

        <div className="status-area" aria-live="polite" aria-atomic="true">
          {error && (
            <p className="message error" role="alert">
              {error}
            </p>
          )}
          {result && (
            <div className="result" role="status">
              <p className="result-label">Your short link</p>
              <div className="result-row">
                <a href={result.shortUrl}>{result.shortUrl}</a>
                <button
                  type="button"
                  className="secondary-button"
                  onClick={copyShortUrl}
                >
                  Copy
                </button>
              </div>
              {copyState === 'copied' && (
                <p className="message success">Copied to clipboard.</p>
              )}
              {copyState === 'failed' && (
                <p className="message error">
                  Could not copy the link. Select it manually.
                </p>
              )}
            </div>
          )}
        </div>
      </section>
    </main>
  );
}

const rootElement = document.getElementById('root');
if (rootElement) {
  createRoot(rootElement).render(<App />);
}
