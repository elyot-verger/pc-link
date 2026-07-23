import socket
import threading
from flask import Flask, jsonify, request
import touchpad_controller
import keyboard_controller
from music_controller import music_bp
from pc_status_controller import pc_status_bp
from keyboard_controller import keyboard_bp
from touchpad_controller import touchpad_bp

app = Flask(__name__)

# Register Blueprints
app.register_blueprint(music_bp)
app.register_blueprint(pc_status_bp)
app.register_blueprint(keyboard_bp, url_prefix='/keyboard')
app.register_blueprint(touchpad_bp, url_prefix='/mouse')

def udp_server():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(('0.0.0.0', 5001))
    while True:
        try:
            data, addr = sock.recvfrom(1024)
            msg = data.decode('utf-8').strip()
            parts = msg.split(':')
            if not parts:
                continue
                
            cmd = parts[0]
            if cmd == 'M' and len(parts) >= 3:
                dx = float(parts[1])
                dy = float(parts[2])
                touchpad_controller.move_mouse(dx, dy)
            elif cmd == 'S' and len(parts) >= 2:
                dy = float(parts[1])
                touchpad_controller.scroll_mouse(dy)
            elif cmd == 'Z' and len(parts) >= 2:
                dy = float(parts[1])
                touchpad_controller.zoom(dy)
            elif cmd == 'C' and len(parts) >= 2:
                button = parts[1]
                touchpad_controller.click_mouse(button)
            elif cmd == 'MD' and len(parts) >= 2:
                button = parts[1]
                touchpad_controller.mouse_down(button)
            elif cmd == 'MU' and len(parts) >= 2:
                button = parts[1]
                touchpad_controller.mouse_up(button)
            elif cmd == 'G' and len(parts) >= 2:
                gesture = parts[1]
                touchpad_controller.trigger_gesture(gesture)
        except Exception as e:
            print("UDP Server error:", e)

# Start UDP server in background
threading.Thread(target=udp_server, daemon=True).start()

@app.before_request
def log_request_info():
    if not request.path.endswith('/network_info'):
        net_type = "Tailscale" if request.remote_addr.startswith("100.") else "Local Network"
        print(f"[Network] Request to {request.path} from {request.remote_addr} ({net_type})")

@app.route('/network_info', methods=['GET'])
def network_info():
    import subprocess
    ips = []
    try:
        res = subprocess.run(["ip", "-4", "addr", "show"], capture_output=True, text=True)
        for line in res.stdout.split('\n'):
            if 'inet ' in line:
                parts = line.split()
                ip = parts[1].split('/')[0]
                if ip != '127.0.0.1' and not ip.startswith('100.'):
                    ips.append(ip)
    except Exception as e:
        pass
    return jsonify({"ips": ips}), 200

@app.route('/ping', methods=['GET'])
def ping():
    return jsonify({"status": "pong"}), 200

if __name__ == '__main__':
    # Listen on all interfaces so the phone can reach it via Tailscale or Local IP
    app.run(host='0.0.0.0', port=5000)
