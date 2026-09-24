// Optional local preview: node docs/prototype/serve.cjs
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const assets = {'/':'index.html','/index.html':'index.html','/style.css':'style.css','/app.js':'app.js'};
http.createServer((req,res) => {
  const name = assets[req.url.split('?')[0]];
  if (!name) { res.writeHead(404); res.end('Not found'); return; }
  res.setHeader('Content-Type', ({'.html':'text/html','.css':'text/css','.js':'text/javascript'})[path.extname(name)]+'; charset=utf-8');
  fs.createReadStream(path.join(__dirname,name)).pipe(res);
}).listen(4173,'127.0.0.1',()=>console.log('DayTrace prototype: http://127.0.0.1:4173'));
