# Meme Coin Overlay (Safari bookmarklet)

A draggable overlay panel you launch on Axiom / GMGN (or any token page) from a
Safari bookmark. This is the v0.1 skeleton — it proves the install works on iOS
before the real feature set goes in.

## What v0.1 does

- Draggable, collapsible panel that survives page scroll (position remembered).
- Detects the token contract address from the URL, falling back to page text.
- One-tap copy of the CA.
- Cross-links the same token to Axiom, GMGN, Dexscreener, Photon, Bullx, Solscan.
- Re-detects automatically when Axiom/GMGN client-side route to a new token.

## Install on iOS Safari

1. Bookmark any page (share sheet → Add Bookmark). Name it `OV`.
2. Copy the whole contents of [`dist/bookmarklet.txt`](dist/bookmarklet.txt).
3. Bookmarks → Edit → tap the `OV` bookmark → select the URL field, clear it,
   paste. Done.
4. On a token page, tap the address bar, type `OV`, and pick the bookmark.

Tap it again to hide the panel, once more to show it. `×` removes it entirely.

## Build

```sh
node build.js     # src/overlay.js -> dist/bookmarklet.txt
```

No dependencies. Edit `src/overlay.js`, rebuild, re-paste the bookmark URL.

## Known constraints

- A site with a strict Content-Security-Policy can block parts of an injected
  script. Everything here is inline and self-contained for that reason — no
  remote loading, so there is nothing for `script-src` to reject.
- The bookmarklet is ~11 KB of URL. That is fine for Safari, but it does mean
  a feature update requires re-pasting the bookmark.
- Anything that needs live price/holder data will require a network call to a
  public API; `connect-src` on the host site may refuse it. That gets tested
  per-feature as we add them.
