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
            
            try:
                # Use python -m yt_dlp directly to ensure it uses the installed package
                cmd = [
                    sys.executable, "-m", "yt_dlp",
                    "-g",
                    "-f", "bestaudio",
                    video_url
                ]
                # Run the command and capture the output url
                result = subprocess.run(cmd, capture_output=True, text=True, check=True)
                stream_url = result.stdout.strip()
                
                if stream_url:
                    print(f"[Server] Successfully resolved stream URL!", flush=True)
                    response_data = {"url": stream_url}
                    self.send_response(200)
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Access-Control-Allow-Origin", "*")
                    self.end_headers()
                    self.wfile.write(json.dumps(response_data).encode('utf-8'))
                    return
                else:
                    raise Exception("Empty URL returned by yt-dlp")
            except Exception as e:
                print(f"[Server] Error resolving stream: {e}", flush=True)
                self.send_response(500)
                self.send_header("Content-Type", "application/json")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(json.dumps({"error": str(e)}).encode('utf-8'))
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
