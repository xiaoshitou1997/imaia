const fs = require('fs');
const src = 'C:/Users/xiaoshitou/AppData/Local/hermes/cache/scratch/imaia-web-out/';
const dst = 'D:/code/github/imaia/imaia-web/';

function walkSync(dir, cb) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const path = dir + (dir.endsWith('/') ? '' : '/') + entry.name;
    if (entry.isDirectory()) walkSync(path, cb);
    else cb(path, path.slice(src.length));
  }
}
walkSync(src, (from, rel) => {
  fs.copyFileSync(from, dst + rel);
  console.log('copied ' + rel);
});

// 删除被 v0.2 取代/砍掉的旧页面
const dead = [
  'src/routes/_authenticated/bot/actions.tsx',
  'src/routes/_authenticated/bot/contacts.tsx',
  'src/routes/_authenticated/bot/messages.tsx',
  'src/routes/_authenticated/bot/directory.tsx',
  'src/routes/_authenticated/bot/console.tsx',
];
for (const rel of dead) {
  try { fs.unlinkSync(dst + rel); console.log('deleted ' + rel); }
  catch (e) { console.log('skip ' + rel + ': ' + e.message); }
}
