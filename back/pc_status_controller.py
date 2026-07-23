import subprocess
import re
from flask import Blueprint, jsonify, request
from device_manager import get_env

pc_status_bp = Blueprint('pc_status_bp', __name__)

def get_topology_info():
    is_wayland = get_env().get('XDG_SESSION_TYPE', '').lower() == 'wayland'
    desktop_env = get_env().get('XDG_CURRENT_DESKTOP', '').lower()
    monitors = []
    
    if is_wayland and 'gnome' in desktop_env:
        try:
            res = subprocess.run(["/home/elyot/.local/bin/gnome-monitor-config", "list"], env=get_env(), capture_output=True, text=True)
            lines = res.stdout.splitlines()
            current_monitor = None
            for line in lines:
                if line.startswith("Monitor ["):
                    parts = line.split()
                    conn = parts[2]
                    status = parts[4] if len(parts) > 4 else "ON"
                    current_monitor = {"id": conn, "name": conn, "is_active": status == "ON", "is_primary": False, "x": 0, "y": 0, "width": 1920, "height": 1080}
                    monitors.append(current_monitor)
                elif line.startswith("  display-name:"):
                    if current_monitor:
                        current_monitor["name"] = line.split(":", 1)[1].strip()
            
            current_log_geom = None
            current_log_prim = False
            for line in lines:
                if line.startswith("Logical monitor"):
                    geom = line.split("[")[1].split("]")[0].strip()
                    current_log_geom = geom
                    current_log_prim = "PRIMARY" in line
                elif line.startswith("  ") and current_log_geom and not "@" in line and not "display-name" in line:
                    conn = line.strip()
                    for m in monitors:
                        if m["id"] == conn:
                            if "+" in current_log_geom:
                                size, pos = current_log_geom.split("+", 1)
                                w, h = size.split("x")
                                x, y = pos.split("+")
                                m["width"] = int(w)
                                m["height"] = int(h)
                                m["x"] = int(x)
                                m["y"] = int(y)
                            m["is_primary"] = current_log_prim
                            m["is_active"] = True
                    current_log_geom = None
            
            primary_mons = [m for m in monitors if m["is_primary"]]
            if len(primary_mons) > 1:
                true_primary = None
                try:
                    xrandr_res = subprocess.run(["xrandr", "--query"], capture_output=True, text=True)
                    for l in xrandr_res.stdout.splitlines():
                        if " connected primary " in l:
                            true_primary = l.split()[0]
                            break
                except Exception:
                    pass
                
                for m in monitors:
                    if m["is_primary"]:
                        if true_primary and m["id"] == true_primary:
                            m["is_primary"] = True
                        elif true_primary:
                            m["is_primary"] = False
                        else:
                            if m["id"] != primary_mons[0]["id"]:
                                m["is_primary"] = False

        except Exception as e:
            print("GNOME wayland error", e)
    else:
        try:
            res = subprocess.run(["xrandr", "--query"], env=get_env(), capture_output=True, text=True)
            lines = res.stdout.splitlines()
            for line in lines:
                if " connected" in line:
                    parts = line.split()
                    conn = parts[0]
                    is_primary = "primary" in line
                    is_active = False
                    x, y, w, h = 0, 0, 1920, 1080
                    geom_match = re.search(r'(\d+)x(\d+)\+(\d+)\+(\d+)', line)
                    if geom_match:
                        is_active = True
                        w, h, x, y = map(int, geom_match.groups())
                    
                    monitors.append({
                        "id": conn,
                        "name": conn,
                        "is_active": is_active,
                        "is_primary": is_primary,
                        "x": x, "y": y, "width": w, "height": h
                    })
        except Exception:
            pass
    return monitors

@pc_status_bp.route('/status', methods=['GET'])
def get_pc_status():
    try:
        vol_res = subprocess.run(["wpctl", "get-volume", "@DEFAULT_AUDIO_SINK@"], capture_output=True, text=True, env=get_env())
        vol_str = vol_res.stdout.strip()
        volume = 0.0
        is_muted = False
        if "Volume:" in vol_str:
            try:
                volume = float(vol_str.split(":")[1].strip().split()[0])
            except ValueError:
                pass
            if "[MUTED]" in vol_str:
                is_muted = True

        bat_state = "unknown"
        bat_pct = "0%"
        try:
            bat_path_res = subprocess.run("upower -e | grep BAT", shell=True, capture_output=True, text=True)
            bat_path = bat_path_res.stdout.strip().split('\n')[0]
            if bat_path:
                bat_res = subprocess.run(["upower", "-i", bat_path], capture_output=True, text=True)
                for line in bat_res.stdout.split('\n'):
                    if "state:" in line:
                        bat_state = line.split(":")[1].strip()
                    elif "percentage:" in line:
                        bat_pct = line.split(":")[1].strip()
        except Exception:
            pass

        display_env = "Inconnu"
        locked = False
        
        if subprocess.run("pgrep -x i3", shell=True).returncode == 0:
            display_env = "i3"
            if subprocess.run("pgrep -x i3lock", shell=True).returncode == 0:
                locked = True
        elif subprocess.run("pgrep -x gnome-shell", shell=True).returncode == 0:
            display_env = "GNOME"
            try:
                lock_res = subprocess.run(["dbus-send", "--session", "--print-reply", "--dest=org.gnome.ScreenSaver", "/org/gnome/ScreenSaver", "org.gnome.ScreenSaver.GetActive"], capture_output=True, text=True, env=get_env())
                if "boolean true" in lock_res.stdout:
                    locked = True
            except Exception:
                pass

        network_type = "none"
        wifi_on = False
        try:
            nm_res = subprocess.run(["nmcli", "-t", "-f", "TYPE,STATE", "dev"], capture_output=True, text=True)
            if "ethernet:connected" in nm_res.stdout:
                network_type = "ethernet"
            elif "wifi:connected" in nm_res.stdout:
                network_type = "wifi"
                
            wifi_radio = subprocess.run(["nmcli", "radio", "wifi"], capture_output=True, text=True)
            if "enabled" in wifi_radio.stdout:
                wifi_on = True
        except Exception:
            pass

        bluetooth_on = False
        try:
            bt_res = subprocess.run(["bluetoothctl", "show"], capture_output=True, text=True)
            if "Powered: yes" in bt_res.stdout:
                bluetooth_on = True
        except Exception:
            pass

        connected_wifi_ssid = ""
        try:
            nm_wifi = subprocess.run(["nmcli", "-t", "-f", "ACTIVE,SSID", "dev", "wifi"], capture_output=True, text=True)
            for line in nm_wifi.stdout.splitlines():
                if line.startswith("yes:"):
                    connected_wifi_ssid = line[4:]
                    break
        except Exception:
            pass
            
        connected_bluetooth_count = 0
        try:
            bt_conn = subprocess.run(["bluetoothctl", "devices", "Connected"], capture_output=True, text=True)
            connected_bluetooth_count = len([line for line in bt_conn.stdout.splitlines() if line.strip()])
        except Exception:
            pass

        power_profile = "balanced"
        try:
            pp_res = subprocess.run(["powerprofilesctl", "get"], capture_output=True, text=True)
            if pp_res.stdout:
                power_profile = pp_res.stdout.strip()
        except Exception:
            pass

        monitor_count = 0
        try:
            drm_res = subprocess.run('grep -c "^connected" /sys/class/drm/*/status', shell=True, capture_output=True, text=True)
            monitor_count = int(drm_res.stdout.strip())
        except Exception:
            pass

        return jsonify({
            "volume": volume,
            "is_muted": is_muted,
            "battery_state": bat_state,
            "battery_percentage": bat_pct,
            "screen_locked": locked,
            "desktop_env": display_env,
            "network_type": network_type,
            "wifi_on": wifi_on,
            "bluetooth_on": bluetooth_on,
            "connected_wifi_ssid": connected_wifi_ssid,
            "connected_bluetooth_count": connected_bluetooth_count,
            "power_profile": power_profile,
            "connected_monitor_count": monitor_count
        }), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500

@pc_status_bp.route('/power_profile/<profile>', methods=['GET', 'POST'])
def set_power_profile(profile):
    try:
        subprocess.run(["powerprofilesctl", "set", profile], check=True)
        return jsonify({"status": "ok"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@pc_status_bp.route('/bluetooth_toggle', methods=['GET', 'POST'])
def toggle_bluetooth():
    try:
        bt_res = subprocess.run(["bluetoothctl", "show"], capture_output=True, text=True)
        if "Powered: yes" in bt_res.stdout:
            subprocess.run(["bluetoothctl", "power", "off"])
        else:
            subprocess.run(["bluetoothctl", "power", "on"])
        return jsonify({"status": "ok"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@pc_status_bp.route('/volume/<action>', methods=['GET'])
def set_pc_volume(action):
    try:
        if action == "up":
            subprocess.run(["wpctl", "set-volume", "@DEFAULT_AUDIO_SINK@", "5%+"], env=get_env())
        elif action == "down":
            subprocess.run(["wpctl", "set-volume", "@DEFAULT_AUDIO_SINK@", "5%-"], env=get_env())
        elif action == "mute":
            subprocess.run(["wpctl", "set-mute", "@DEFAULT_AUDIO_SINK@", "toggle"], env=get_env())
        else:
            try:
                vol = float(action)
                subprocess.run(["wpctl", "set-volume", "@DEFAULT_AUDIO_SINK@", str(vol)], env=get_env())
            except ValueError:
                pass
        return jsonify({"status": "success"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@pc_status_bp.route('/lock', methods=['GET'])
def lock_pc():
    try:
        if subprocess.run("pgrep -x i3", shell=True).returncode == 0:
            subprocess.Popen(["i3lock", "-c", "000000"])
        else:
            subprocess.Popen(["dbus-send", "--type=method_call", "--dest=org.gnome.ScreenSaver", "/org/gnome/ScreenSaver", "org.gnome.ScreenSaver.Lock"], env=get_env())
        return jsonify({"status": "success"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@pc_status_bp.route('/wifi_list', methods=['GET'])
def wifi_list():
    try:
        nm_res = subprocess.run(["nmcli", "-t", "-f", "IN-USE,SSID,SIGNAL", "dev", "wifi", "list"], capture_output=True, text=True)
        networks = []
        for line in nm_res.stdout.split('\n'):
            if not line: continue
            connected = line.startswith('*')
            rest = line[2:] if (line.startswith('*') or line.startswith(' ')) and len(line) > 1 and line[1] == ':' else line
            parts = rest.rsplit(':', 1)
            if len(parts) == 2 and parts[0]:
                networks.append({"ssid": parts[0].replace(r'\:', ':'), "signal": parts[1], "connected": connected})
        networks.sort(key=lambda x: not x["connected"])
        return jsonify({"networks": networks}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@pc_status_bp.route('/bluetooth_list', methods=['GET'])
def bluetooth_list():
    try:
        bt_res = subprocess.run(["bluetoothctl", "devices"], capture_output=True, text=True)
        devices = []
        for line in bt_res.stdout.split('\n'):
            if not line: continue
            parts = line.split(' ', 2)
            if len(parts) >= 3:
                mac = parts[1]
                name = parts[2]
                
                info_res = subprocess.run(["bluetoothctl", "info", mac], capture_output=True, text=True)
                connected = False
                icon = "bluetooth"
                for info_line in info_res.stdout.split('\n'):
                    info_line = info_line.strip()
                    if info_line.startswith("Connected: yes"):
                        connected = True
                    elif info_line.startswith("Icon:"):
                        icon = info_line.split(':', 1)[1].strip()
                        
                devices.append({"mac": mac, "name": name, "connected": connected, "icon": icon})
        devices.sort(key=lambda x: not x["connected"])
        return jsonify({"devices": devices}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@pc_status_bp.route('/wifi_toggle', methods=['GET', 'POST'])
def toggle_wifi():
    try:
        nmcli_res = subprocess.run(["nmcli", "radio", "wifi"], capture_output=True, text=True)
        if "enabled" in nmcli_res.stdout:
            subprocess.run(["nmcli", "radio", "wifi", "off"])
        else:
            subprocess.run(["nmcli", "radio", "wifi", "on"])
        return jsonify({"status": "ok"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@pc_status_bp.route('/monitor_list', methods=['GET'])
def monitor_list():
    monitors = []
    try:
        bctl = subprocess.run(["brightnessctl", "-m"], capture_output=True, text=True)
        if bctl.returncode == 0 and bctl.stdout.strip():
            lines = bctl.stdout.strip().split('\n')
            for line in lines:
                parts = line.split(',')
                if len(parts) >= 4 and "backlight" in parts[1]:
                    brightness_pct = parts[3].replace('%', '')
                    monitors.append({
                        "id": "internal",
                        "name": "Laptop Display",
                        "brightness": int(brightness_pct)
                    })
                    break
    except Exception:
        pass

    try:
        ddc_detect = subprocess.run(["ddcutil", "detect", "-t"], capture_output=True, text=True)
        lines = ddc_detect.stdout.strip().split('\n')
        current_disp_id = None
        current_model = None
        for line in lines:
            line = line.strip()
            if line.startswith("Display"):
                parts = line.split()
                if len(parts) >= 2:
                    current_disp_id = parts[1]
            elif line.startswith("Monitor:"):
                parts = line.split(":")
                if len(parts) >= 2:
                    current_model = parts[1].strip()
                    if not current_model:
                        current_model = parts[0].replace("Monitor:", "").strip()
                if current_disp_id and current_model:
                    try:
                        ddc_vcp = subprocess.run(["ddcutil", "getvcp", "10", "--display", current_disp_id, "--terse"], capture_output=True, text=True)
                        if ddc_vcp.returncode == 0:
                            vcp_parts = ddc_vcp.stdout.strip().split()
                            if len(vcp_parts) >= 4:
                                brightness = int(vcp_parts[3])
                                monitors.append({
                                    "id": str(current_disp_id),
                                    "name": current_model,
                                    "brightness": brightness
                                })
                    except Exception:
                        pass
                    current_disp_id = None
                    current_model = None
    except Exception:
        pass
        
    topo = get_topology_info()
    for m in monitors:
        m['is_active'] = True
        m['is_primary'] = False
        
        for t in topo:
            is_topo_internal = t['id'].startswith('eDP') or t['id'] == 'internal'
            is_m_internal = m['id'] == 'internal' or m.get('name', '').lower() == 'laptop display'
            
            if is_topo_internal and is_m_internal:
                m['is_active'] = t['is_active']
                m['is_primary'] = t['is_primary']
                m['name'] = t.get('name', m['name'])
                break
            elif not is_topo_internal and not is_m_internal:
                m['is_active'] = t['is_active']
                m['is_primary'] = t['is_primary']
                m['name'] = t.get('name', m['name'])
                break
                
    return jsonify(monitors), 200

@pc_status_bp.route('/set_brightness/<monitor_id>/<level>', methods=['GET', 'POST'])
def set_brightness(monitor_id, level):
    try:
        level_int = int(level)
        if level_int < 0: level_int = 0
        if level_int > 100: level_int = 100
        
        if monitor_id == "internal":
            subprocess.run(["brightnessctl", "set", f"{level_int}%"])
        else:
            subprocess.run(["ddcutil", "setvcp", "10", str(level_int), "--display", monitor_id])
            
        return jsonify({"status": "ok"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@pc_status_bp.route('/topology_list', methods=['GET'])
def topology_list():
    return jsonify({"monitors": get_topology_info()}), 200

@pc_status_bp.route('/apply_topology', methods=['POST'])
def apply_topology():
    try:
        data = request.json
        monitors = data.get("monitors", [])
        
        is_wayland = get_env().get('XDG_SESSION_TYPE', '').lower() == 'wayland'
        desktop_env = get_env().get('XDG_CURRENT_DESKTOP', '').lower()
        
        if is_wayland and 'gnome' in desktop_env:
            cmd = ["/home/elyot/.local/bin/gnome-monitor-config", "set"]
            for m in monitors:
                if m.get("is_primary"):
                    cmd.extend(["-Lp", "-M", m["id"]])
                else:
                    cmd.extend(["-L", "-M", m["id"]])
                cmd.extend(["-x", str(m["x"]), "-y", str(m["y"])])
                if not m.get("is_active"):
                    pass
            active_mons = [m for m in monitors if m.get("is_active")]
            if active_mons:
                min_x = min(m["x"] for m in active_mons)
                min_y = min(m["y"] for m in active_mons)
                if min_x != 0 or min_y != 0:
                    for i in range(len(cmd)):
                        if cmd[i] == "-x":
                            cmd[i+1] = str(int(cmd[i+1]) - min_x)
                        elif cmd[i] == "-y":
                            cmd[i+1] = str(int(cmd[i+1]) - min_y)
            subprocess.run(cmd, env=get_env())
        else:
            cmd = ["xrandr"]
            for m in monitors:
                if m.get("is_active"):
                    cmd.extend(["--output", m["id"], "--mode", f"{m['width']}x{m['height']}", "--pos", f"{m['x']}x{m['y']}"])
                    if m.get("is_primary"):
                        cmd.append("--primary")
                else:
                    cmd.extend(["--output", m["id"], "--off"])
            subprocess.run(cmd, env=get_env())
            
        return jsonify({"status": "success"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500
