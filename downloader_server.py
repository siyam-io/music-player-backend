import http.server
import json
import urllib.parse
import urllib.request
import sys
import subprocess

import os

PORT = int(os.environ.get("PORT", 5082))

class DownloaderHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # Suppress logging to keep console clean
        return

    def do_GET(self):
        parsed_url = urllib.parse.urlparse(self.path)
        if parsed_url.path == '/stream':
            query = urllib.parse.parse_qs(parsed_url.query)
            video_id = query.get('id', [None])[0]
            if not video_id:
                self.send_error(400, "Missing 'id' parameter")
                return

            stream_url = self.resolve_video_stream(video_id)
            if not stream_url:
                self.send_error(500, "Could not resolve stream URL")
                return

            req_headers = {'User-Agent': 'Mozilla/5.0'}
            range_header = self.headers.get('Range')
            if range_header:
                req_headers['Range'] = range_header

            try:
                req = urllib.request.Request(stream_url, headers=req_headers)
                with urllib.request.urlopen(req, timeout=30) as resp:
                    self.send_response(resp.status)
                    for key, val in resp.headers.items():
                        if key.lower() in ['content-type', 'content-length', 'content-range', 'accept-ranges']:
                            self.send_header(key, val)
                    self.send_header("Access-Control-Allow-Origin", "*")
                    self.end_headers()

                    while True:
                        chunk = resp.read(64 * 1024)
                        if not chunk:
                            break
                        self.wfile.write(chunk)
            except Exception as e:
                print(f"[Server] Stream proxy error for {video_id}: {e}", flush=True)
                return

        elif parsed_url.path == '/resolve':
            query = urllib.parse.parse_qs(parsed_url.query)
            video_id = query.get('id', [None])[0]
            
            if not video_id:
                self.send_error(400, "Missing 'id' parameter")
                return

            print(f"[Server] Resolving stream for video ID: {video_id}...", flush=True)
            resolved_url = self.resolve_video_stream(video_id)
            
            if resolved_url:
                # Return proxied stream URL to bypass YouTube IP locks, with direct_url fallback
                host_header = self.headers.get('Host', f'localhost:{PORT}')
                scheme = 'https' if 'onrender.com' in host_header else 'http'
                proxy_url = f"{scheme}://{host_header}/stream?id={video_id}"
                
                response_data = {
                    "url": proxy_url,
                    "direct_url": resolved_url
                }
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(json.dumps(response_data).encode('utf-8'))
                return
            
            error_msg = f"All format attempts failed for video {video_id}"
            print(f"[Server] {error_msg}", flush=True)
            self.send_response(500)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps({"error": error_msg}).encode('utf-8'))
            return
        
        elif parsed_url.path == '/search':
            query = urllib.parse.parse_qs(parsed_url.query)
            q = query.get('q', [None])[0]
            
            if not q:
                self.send_error(400, "Missing 'q' parameter")
                return

            print(f"[Server] Searching YouTube for: {q}...", flush=True)
            results = self.search_youtube(q)
            
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps(results).encode('utf-8'))
            return

        self.send_error(404, "Not Found")

    def resolve_video_stream(self, video_id):
        video_url = f"https://www.youtube.com/watch?v={video_id}"
        client_options = [
            ["--extractor-args", "youtube:player_client=android,mweb"],
            ["--extractor-args", "youtube:player_client=tv_embedded,web_creator"],
            []
        ]
        
        for client_args in client_options:
            try:
                cmd = [
                    sys.executable, "-m", "yt_dlp",
                    "-g",
                    "-f", "bestaudio/best",
                    "--no-check-certificates",
                    "--no-warnings"
                ] + client_args + [video_url]
                
                result = subprocess.run(cmd, capture_output=True, text=True, timeout=12)
                if result.returncode == 0 and result.stdout.strip():
                    stream_url = result.stdout.strip()
                    if '\n' in stream_url:
                        stream_url = stream_url.split('\n')[-1].strip()
                    if stream_url and stream_url.startswith("http"):
                        print(f"[Server] Successfully resolved stream URL!", flush=True)
                        return stream_url
            except Exception as e:
                print(f"[Server] Resolve attempt error: {e}", flush=True)
        return None

    def search_youtube(self, query_str):
        cmd = [
            sys.executable, "-m", "yt_dlp",
            f"ytsearch10:{query_str}",
            "--dump-json",
            "--flat-playlist",
            "--no-check-certificates",
            "--no-warnings"
        ]
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
            if result.returncode == 0 and result.stdout.strip():
                lines = result.stdout.strip().split('\n')
                results = []
                for line in lines:
                    if not line:
                        continue
                    try:
                        data = json.loads(line)
                        duration = data.get("duration", 0) or 0
                        minutes = int(duration // 60)
                        seconds = int(duration % 60)
                        duration_text = f"{minutes:02d}:{seconds:02d}"
                        
                        thumbnails = data.get("thumbnails", [])
                        thumbnail_url = ""
                        if thumbnails:
                            thumbnail_url = thumbnails[0].get("url", "")
                            
                        results.append({
                            "id": data.get("id", ""),
                            "title": data.get("title", ""),
                            "artist": data.get("uploader", "Unknown Artist"),
                            "durationText": duration_text,
                            "thumbnail": thumbnail_url
                        })
                    except Exception:
                        continue
                if results:
                    return results
        except Exception as e:
            print(f"[Server] Search error: {e}", flush=True)
        return []

def run():
    server_address = ('', PORT)
    httpd = http.server.HTTPServer(server_address, DownloaderHandler)
    print(f"\n=======================================================", flush=True)
    print(f" YouTube Local Stream Resolver Server is running!", flush=True)
    print(f" Port: {PORT}", flush=True)
    print(f" Android Emulator Address: http://10.0.2.2:{PORT}/resolve?id=...", flush=True)
    print(f"=======================================================\n", flush=True)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server...", flush=True)
        httpd.server_close()

if __name__ == '__main__':
    run()
