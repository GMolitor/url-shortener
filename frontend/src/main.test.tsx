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
    expect(
      container.querySelector('label[for="destination-url"]'),
    ).not.toBeNull();
    expect(container.querySelector('button[type="submit"]')?.textContent).toBe(
      'Shorten URL',
    );
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
    await setUrl('https://example.com/article');

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
});
