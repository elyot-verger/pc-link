import urllib.request
import urllib.parse
import json
import subprocess
from flask import Blueprint, jsonify, send_file
from device_manager import get_env

music_bp = Blueprint('music_bp', __name__)

def run_playerctl(action):
    try:
        result = subprocess.run(["playerctl", action], capture_output=True, text=True, env=get_env())
        if result.returncode == 0:
            return jsonify({"status": "success", "action": action}), 200
        else:
            return jsonify({"status": "error", "message": result.stderr}), 500
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@music_bp.route('/play_pause', methods=['GET'])
def play_pause():
    return run_playerctl("play-pause")

@music_bp.route('/next', methods=['GET'])
def next_track():
    return run_playerctl("next")

@music_bp.route('/prev', methods=['GET'])
def prev_track():
    subprocess.run(["playerctl", "previous"], env=get_env())
    return jsonify({"status": "success"})

@music_bp.route('/open_deezer', methods=['GET'])
def open_deezer():
    try:
        subprocess.Popen(["flatpak", "run", "dev.aunetx.deezer"], env=get_env())
        return jsonify({"status": "success"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@music_bp.route('/status', methods=['GET'])
def status():
    try:
        title = subprocess.run(["playerctl", "metadata", "title"], capture_output=True, text=True).stdout.strip()
        artist = subprocess.run(["playerctl", "metadata", "artist"], capture_output=True, text=True).stdout.strip()
        state = subprocess.run(["playerctl", "status"], capture_output=True, text=True).stdout.strip()
        length_str = subprocess.run(["playerctl", "metadata", "mpris:length"], capture_output=True, text=True).stdout.strip()
        album = subprocess.run(["playerctl", "metadata", "album"], capture_output=True, text=True).stdout.strip()
        
        year = subprocess.run(["playerctl", "metadata", "year"], capture_output=True, text=True).stdout.strip()
        if not year:
            year = subprocess.run(["playerctl", "metadata", "date"], capture_output=True, text=True).stdout.strip()
        if not year:
            year = subprocess.run(["playerctl", "metadata", "xesam:contentCreated"], capture_output=True, text=True).stdout.strip()
        if len(year) > 4:
            year = year[:4]
        
        length = int(length_str) / 1000000.0 if length_str.isdigit() else 0.0
        
        try:
            position_str = subprocess.run(["playerctl", "position"], capture_output=True, text=True).stdout.strip()
            position = float(position_str) if position_str else 0.0
        except ValueError:
            position = 0.0

        try:
            loop_res = subprocess.run(["playerctl", "loop"], capture_output=True, text=True)
            if "not supported" in loop_res.stderr.lower() or loop_res.returncode != 0:
                loop_state = "Unsupported"
            else:
                loop_state = loop_res.stdout.strip()
                if not loop_state:
                    loop_state = "Unsupported"
        except Exception:
            loop_state = "Unsupported"

        try:
            vol_str = subprocess.run(["playerctl", "volume"], capture_output=True, text=True).stdout.strip()
            volume = float(vol_str) if vol_str else -1.0
        except Exception:
            volume = -1.0

        try:
            shuffle_res = subprocess.run(["playerctl", "shuffle"], capture_output=True, text=True)
            if "not supported" in shuffle_res.stderr.lower() or shuffle_res.returncode != 0:
                shuffle_state = "Unsupported"
            else:
                shuffle_state = shuffle_res.stdout.strip()
                if not shuffle_state:
                    shuffle_state = "Unsupported"
        except Exception:
            shuffle_state = "Unsupported"

        return jsonify({
            "title": title,
            "artist": artist,
            "state": state,
            "length": length,
            "position": position,
            "loop": loop_state,
            "shuffle": shuffle_state,
            "volume": volume,
            "album": album,
            "year": year
        }), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@music_bp.route('/seek/<float:position>', methods=['GET'])
def seek(position):
    try:
        result = subprocess.run(["playerctl", "position", str(position)], capture_output=True, text=True)
        if result.returncode == 0:
            return jsonify({"status": "success", "position": position}), 200
        else:
            return jsonify({"status": "error", "message": result.stderr}), 500
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@music_bp.route('/volume/<float:vol>', methods=['GET'])
def set_volume(vol):
    try:
        result = subprocess.run(["playerctl", "volume", str(vol)], capture_output=True, text=True)
        if result.returncode == 0:
            return jsonify({"status": "success", "volume": vol}), 200
        else:
            return jsonify({"status": "error", "message": result.stderr}), 500
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@music_bp.route('/shuffle', methods=['GET'])
def toggle_shuffle():
    try:
        subprocess.run(["playerctl", "shuffle", "Toggle"], capture_output=True)
        return jsonify({"status": "success"}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@music_bp.route('/loop', methods=['GET'])
def toggle_loop():
    try:
        current = subprocess.run(["playerctl", "loop"], capture_output=True, text=True).stdout.strip().lower()
        if current == "none":
            next_state = "Playlist"
        elif current == "playlist":
            next_state = "Track"
        else:
            next_state = "None"
        subprocess.run(["playerctl", "loop", next_state], capture_output=True)
        return jsonify({"status": "success", "loop": next_state}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@music_bp.route('/art', methods=['GET'])
def get_art():
    try:
        art_url = subprocess.run(["playerctl", "metadata", "mpris:artUrl"], capture_output=True, text=True).stdout.strip()
        if not art_url:
            return "No art", 404
        
        if art_url.startswith("file://"):
            filepath = art_url[7:]
            return send_file(filepath)
        elif art_url.startswith("http"):
            req = urllib.request.Request(art_url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req) as response:
                return response.read(), 200, {'Content-Type': response.headers.get('Content-Type')}
        else:
            return "Unknown protocol", 400
    except Exception as e:
        return str(e), 500

@music_bp.route('/lyrics', methods=['GET'])
def get_lyrics():
    try:
        title = subprocess.run(["playerctl", "metadata", "title"], capture_output=True, text=True).stdout.strip()
        artist = subprocess.run(["playerctl", "metadata", "artist"], capture_output=True, text=True).stdout.strip()
        
        if not title:
            return jsonify({"error": "No track playing"}), 404
            
        url = f"https://lrclib.net/api/get?track_name={urllib.parse.quote(title)}&artist_name={urllib.parse.quote(artist)}"
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 distant-lock-button/1.0'})
        
        try:
            with urllib.request.urlopen(req) as response:
                if response.status == 200:
                    data = json.loads(response.read().decode('utf-8'))
                    return jsonify(data), 200
                else:
                    return jsonify({"error": "Lyrics not found"}), 404
        except urllib.error.HTTPError as e:
            return jsonify({"error": f"HTTP Error: {e.code}"}), 404
    except Exception as e:
        return jsonify({"error": str(e)}), 500
