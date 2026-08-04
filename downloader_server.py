import http.server
import json
import urllib.parse
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
        if parsed_url.path == '/resolve':
            query = urllib.parse.parse_qs(parsed_url.query)
            video_id = query.get('id', [None])[0]
            
            if not video_id:
                self.send_error(400, "Missing 'id' parameter")
                return

            print(f"[Server] Resolving stream for video ID: {video_id}...", flush=True)
            video_url = f"https://www.youtube.com/watch?v={video_id}"
            
            # Try multiple format options as fallback
            format_options = [
                "bestaudio[ext=m4a]/bestaudio/best",
                "bestaudio",
                "best",
            ]
            
            last_error = None
            for fmt in format_options:
                try:
                    cmd = [
                        sys.executable, "-m", "yt_dlp",
                        "-g",
                        "-f", fmt,
                        "--no-check-certificates",
                        "--no-warnings",
                        "--extractor-retries", "3",
                        video_url
                    ]
                    print(f"[Server] Trying format: {fmt}", flush=True)
                    result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
                    
                    if result.returncode != 0:
                        stderr_msg = result.stderr.strip()
                        print(f"[Server] yt-dlp failed with format '{fmt}': {stderr_msg}", flush=True)
                        last_error = stderr_msg or f"Exit code {result.returncode}"
                        continue
                    
                    stream_url = result.stdout.strip()
                    # If multiple URLs returned (video+audio), take the last one (audio)
                    if '\n' in stream_url:
                        stream_url = stream_url.split('\n')[-1].strip()
                    
                    if stream_url:
                        print(f"[Server] Successfully resolved stream URL with format '{fmt}'!", flush=True)
                        response_data = {"url": stream_url}
                        self.send_response(200)
                        self.send_header("Content-Type", "application/json")
                        self.send_header("Access-Control-Allow-Origin", "*")
                        self.end_headers()
                        self.wfile.write(json.dumps(response_data).encode('utf-8'))
                        return
                    else:
                        last_error = "Empty URL returned by yt-dlp"
                        print(f"[Server] Empty URL with format '{fmt}'", flush=True)
                except subprocess.TimeoutExpired:
                    last_error = "yt-dlp timed out"
                    print(f"[Server] Timeout with format '{fmt}'", flush=True)
                except Exception as e:
                    last_error = str(e)
                    print(f"[Server] Exception with format '{fmt}': {e}", flush=True)
            
            # All formats failed
            error_msg = f"All format attempts failed. Last error: {last_error}"
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
            try:
                cmd = [
                    sys.executable, "-m", "yt_dlp",
                    f"ytsearch10:{q}",
                    "--dump-json",
                    "--flat-playlist",
                    "--quiet"
                ]
                result = subprocess.run(cmd, capture_output=True, text=True, check=True)
                lines = result.stdout.strip().split('\n')
                results = []
                for line in lines:
                    if not line:
                        continue
                    data = json.loads(line)
                    duration = data.get("duration", 0)
                    if duration is None:
                        duration = 0
                    minutes = int(duration // 60)
                    seconds = int(duration % 60)
                    duration_text = f"{minutes:02d}:{seconds:02d}"
                    
                    # Extract best thumbnail
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
                
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(json.dumps(results).encode('utf-8'))
                return
            except Exception as e:
                print(f"[Server] Error searching: {e}", flush=True)
                self.send_response(500)
                self.send_header("Content-Type", "application/json")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(json.dumps({"error": str(e)}).encode('utf-8'))
                return

        self.send_error(404, "Not Found")

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
