#!/usr/bin/env node
// Turns src/overlay.js into a pasteable javascript: bookmarklet.
// No dependencies on purpose — `node build.js` is the whole toolchain.

const fs = require('fs');
const path = require('path');

const src = fs.readFileSync(path.join(__dirname, 'src/overlay.js'), 'utf8');

// Conservative squeeze: drop whole-line // comments, trim indentation, keep
// newlines so automatic semicolon insertion can't bite us.
const squeezed = src
  .split('\n')
  .map((l) => l.trim())
  .filter((l) => l && !l.startsWith('//'))
  .join('\n');

const bookmarklet = 'javascript:' + encodeURIComponent(squeezed);

fs.mkdirSync(path.join(__dirname, 'dist'), { recursive: true });
fs.writeFileSync(path.join(__dirname, 'dist/overlay.min.js'), squeezed);
fs.writeFileSync(path.join(__dirname, 'dist/bookmarklet.txt'), bookmarklet);

const kb = (bookmarklet.length / 1024).toFixed(1);
console.log(`dist/bookmarklet.txt — ${bookmarklet.length} chars (${kb} KB)`);
