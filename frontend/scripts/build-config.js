const fs = require('node:fs');
const path = require('node:path');

const frontendRoot = path.resolve(__dirname, '..');
const outputDirectory = path.join(frontendRoot, 'dist');
const rawUrl = (process.env.CAMPUSCARE_API_BASE_URL || '').trim();
if (!rawUrl) {
  throw new Error('Set CAMPUSCARE_API_BASE_URL in the Vercel project environment variables.');
}

let apiUrl;
try {
  apiUrl = new URL(rawUrl);
} catch {
  throw new Error('CAMPUSCARE_API_BASE_URL must be an absolute HTTPS URL.');
}
if (apiUrl.protocol !== 'https:' || apiUrl.username || apiUrl.password || apiUrl.search || apiUrl.hash) {
  throw new Error('CAMPUSCARE_API_BASE_URL must be HTTPS and must not contain credentials, a query, or a fragment.');
}

const baseUrl = rawUrl.replace(/\/+$/, '');
const output = `// Generated at build time from Vercel environment configuration.\nwindow.CAMPUSCARE_API_BASE_URL = ${JSON.stringify(baseUrl)};\n`;

fs.rmSync(outputDirectory, { recursive: true, force: true });
fs.mkdirSync(outputDirectory, { recursive: true });

for (const entry of fs.readdirSync(frontendRoot, { withFileTypes: true })) {
  if (entry.isFile() && entry.name.endsWith('.html')) {
    fs.copyFileSync(path.join(frontendRoot, entry.name), path.join(outputDirectory, entry.name));
  }
}

for (const directory of ['css', 'js']) {
  fs.cpSync(path.join(frontendRoot, directory), path.join(outputDirectory, directory), { recursive: true });
}

fs.writeFileSync(path.join(outputDirectory, 'js', 'deployment-config.js'), output, 'utf8');
console.log(`Frontend built for ${baseUrl} in ${path.relative(frontendRoot, outputDirectory)}.`);
