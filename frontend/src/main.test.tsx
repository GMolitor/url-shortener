import { act } from 'react';
import { createRoot, Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './main';

(
  globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

describe('create-link workflow', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(async () => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () => root.render(<App />));
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
  });

  async function setUrl(value: string) {
    await act(async () => {
      const input =
        container.querySelector<HTMLInputElement>('#destination-url')!;
      const setValue = Object.getOwnPropertyDescriptor(
        HTMLInputElement.prototype,
        'value',
      )!.set!;
      setValue.call(input, value);
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
  }

  async function submit() {
    await act(async () => {
      container
        .querySelector('form')!
        .dispatchEvent(
          new Event('submit', { bubbles: true, cancelable: true }),
        );
    });
  }

  it('renders an accessible URL form', () => {
    expect(container.querySelector('h1')?.textContent).toBe('Shorten a URL');
    const input =
      container.querySelector<HTMLInputElement>('#destination-url')!;
    expect(
      container.querySelector('label[for="destination-url"]'),
    ).not.toBeNull();
    expect(input.required).toBe(true);
    expect(input.getAttribute('aria-describedby')).toBe('url-help');
    expect(
      container.querySelector('.status-area')?.getAttribute('aria-live'),
    ).toBe('polite');
    expect(container.querySelector('button[type="submit"]')?.textContent).toBe(
      'Shorten URL',
    );
  });

  it('shows a loading state and disables the form while creating a link', async () => {
    let resolveRequest!: (response: Response) => void;
    const request = new Promise<Response>((resolve) => {
      resolveRequest = resolve;
    });
    vi.spyOn(globalThis, 'fetch').mockReturnValue(request);
    await setUrl('https://example.com');

    act(() => {
      container
        .querySelector('form')!
        .dispatchEvent(
          new Event('submit', { bubbles: true, cancelable: true }),
        );
    });

    expect(
      container.querySelector<HTMLInputElement>('#destination-url')!.disabled,
    ).toBe(true);
    expect(
      container.querySelector<HTMLButtonElement>('button[type="submit"]')!
        .disabled,
    ).toBe(true);
    expect(
      container.querySelector('button[type="submit"]')?.textContent,
    ).toContain('Creating');

    resolveRequest(
      new Response(
        JSON.stringify({ shortUrl: 'http://localhost:8080/Abc1234' }),
        {
          status: 201,
        },
      ),
    );
    await act(async () => request);
    expect(
      container.querySelector<HTMLInputElement>('#destination-url')!.disabled,
    ).toBe(false);
  });

  it('rejects invalid URLs before calling the API', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    await setUrl('ftp://example.com');

    await submit();

    expect(container.querySelector('[role="alert"]')?.textContent).toContain(
      'HTTP or HTTPS',
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('submits a valid URL and displays the returned short link', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 'Abc1234',
          shortUrl: 'http://localhost:8080/Abc1234',
          destinationUrl: 'https://example.com/article',
          createdAt: '2026-09-01T19:30:00Z',
        }),
        { status: 201, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    await setUrl('  https://example.com/article  ');

    await submit();

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/links', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url: 'https://example.com/article' }),
    });
    expect(container.querySelector('.result a')?.textContent).toBe(
      'http://localhost:8080/Abc1234',
    );
  });

  it('displays a server error message', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ message: 'The URL is not allowed.' }), {
        status: 400,
      }),
    );
    await setUrl('https://example.com');

    await submit();

    expect(container.querySelector('[role="alert"]')?.textContent).toBe(
      'The URL is not allowed.',
    );
  });

  it('uses a safe fallback for network and malformed API failures', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    fetchMock.mockRejectedValueOnce(new Error('Network unavailable'));
    await setUrl('https://example.com');
    await submit();
    expect(container.querySelector('[role="alert"]')?.textContent).toBe(
      'Network unavailable',
    );

    fetchMock.mockResolvedValueOnce(new Response('not json', { status: 500 }));
    await submit();
    expect(container.querySelector('[role="alert"]')?.textContent).toBe(
      'The link could not be created. Try again.',
    );
  });

  it('rejects credentials and control characters before calling the API', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    await setUrl('https://user:password@example.com');
    await submit();
    expect(container.querySelector('[role="alert"]')?.textContent).toContain(
      'embedded credentials',
    );
    expect(fetchMock).not.toHaveBeenCalled();

    await setUrl('https://example.com/path\u0001next');
    await submit();
    expect(container.querySelector('[role="alert"]')?.textContent).toContain(
      'control characters',
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('copies the short link and confirms the action', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 'Abc1234',
          shortUrl: 'http://localhost:8080/Abc1234',
        }),
        {
          status: 201,
        },
      ),
    );
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    });
    await setUrl('https://example.com');
    await submit();

    await act(async () => {
      container.querySelector<HTMLButtonElement>('.secondary-button')!.click();
    });

    expect(writeText).toHaveBeenCalledWith('http://localhost:8080/Abc1234');
    expect(container.textContent).toContain('Copied to clipboard.');
  });

  it('shows a recoverable message when clipboard access fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ shortUrl: 'http://localhost:8080/Abc1234' }),
        {
          status: 201,
        },
      ),
    );
    const writeText = vi.fn().mockRejectedValue(new Error('Clipboard blocked'));
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    });
    await setUrl('https://example.com');
    await submit();

    await act(async () => {
      container.querySelector<HTMLButtonElement>('.secondary-button')!.click();
    });

    expect(container.textContent).toContain(
      'Could not copy the link. Select it manually.',
    );
    expect(container.querySelector('.message.error')).not.toBeNull();
  });
});
