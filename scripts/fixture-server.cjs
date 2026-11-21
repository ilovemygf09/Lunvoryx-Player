// Local integration fixtures. Run with `node scripts/fixture-server.cjs`.
// Only listens on loopback. Android Emulator reaches it at 10.0.2.2:8765.
const http = require('node:http');
const zlib = require('node:zlib');
const stamp = ms => new Date(ms).toISOString().replace(/[-:T]/g, '').slice(0, 14) + ' +0000';
const playlist = `#EXTM3U
#EXTINF:-1 tvg-id="test.live" group-title="General",Canal de prueba
https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
#EXTINF:-1 tvg-id="test.movie" group-title="Películas",Sintel de prueba
https://storage.googleapis.com/shaka-demo-assets/sintel/dash.mpd
#EXTINF:-1 tvg-id="test.error" group-title="General",Canal sin señal
http://10.0.2.2:8765/missing.m3u8
`;
http.createServer((req, res) => {
    if (req.url === '/library.m3u') {
        res.writeHead(200, {'Content-Type': 'audio/x-mpegurl'}); res.end(playlist); return;
    }
    if (req.url === '/guide.xml' || req.url === '/guide.xml.gz') {
        const now = Date.now();
        const xml = `<?xml version="1.0" encoding="UTF-8"?><tv><channel id="test.live"><display-name>Canal de prueba</display-name></channel><programme channel="test.live" start="${stamp(now-1800000)}" stop="${stamp(now+1800000)}"><title>Programa de prueba actual</title><desc>Fixture local para verificar XMLTV.</desc></programme><programme channel="test.live" start="${stamp(now+1800000)}" stop="${stamp(now+5400000)}"><title>Siguiente programa</title></programme></tv>`;
        res.writeHead(200, {'Content-Type': req.url.endsWith('.gz') ? 'application/gzip' : 'text/xml'});
        res.end(req.url.endsWith('.gz') ? zlib.gzipSync(xml) : xml); return;
    }
    if(req.url === '/invalid.m3u') {res.writeHead(200, {'Content-Type':'text/html'});res.end('<html>Not a playlist</html>');return;}
    res.writeHead(404, {'Content-Type':'text/plain'});res.end('Not found');
}).listen(8765, '127.0.0.1', () => process.stdout.write('Fixtures ready on 127.0.0.1:8765\n'));
