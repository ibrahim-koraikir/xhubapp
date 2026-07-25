import urllib.request
import os
import time

url = "http://mirrors.aliyun.com/macports/distfiles/gradle/gradle-8.14.3-bin.zip"
out_path = "gradle-8.14.3-bin.zip"

def download_file(url, out_path, chunk_size=8192):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    retries = 5
    for i in range(retries):
        try:
            print(f"Downloading from {url}...")
            with urllib.request.urlopen(req, timeout=30) as response:
                total_length = int(response.headers.get('content-length'))
                print(f"File size: {total_length} bytes")
                with open(out_path, 'wb') as f:
                    downloaded = 0
                    while True:
                        chunk = response.read(chunk_size)
                        if not chunk:
                            break
                        f.write(chunk)
                        downloaded += len(chunk)
                        if downloaded % (1024 * 1024 * 5) == 0:
                            print(f"Downloaded {downloaded / 1024 / 1024} MB")
            print("Download successful.")
            return
        except Exception as e:
            print(f"Error: {e}. Retrying in 2 seconds...")
            time.sleep(2)
    print("Download failed after retries.")

download_file(url, out_path)
