import time
import subprocess
from flask import Blueprint, jsonify, request
from device_manager import keyboard_ui, get_env
try:
    from evdev import ecodes as e
except ImportError:
    pass

keyboard_bp = Blueprint('keyboard_bp', __name__)
current_keyboard_layout = "qwerty"

def tap_key(keycode):
    if keyboard_ui:
        keyboard_ui.write(e.EV_KEY, keycode, 1)
        keyboard_ui.syn()
        time.sleep(0.05)
        keyboard_ui.write(e.EV_KEY, keycode, 0)
        keyboard_ui.syn()

def tap_key_with_mods(keycode, mods):
    if keyboard_ui:
        for mod in mods:
            keyboard_ui.write(e.EV_KEY, mod, 1)
        keyboard_ui.write(e.EV_KEY, keycode, 1)
        keyboard_ui.syn()
        time.sleep(0.05)
        keyboard_ui.write(e.EV_KEY, keycode, 0)
        for mod in reversed(mods):
            keyboard_ui.write(e.EV_KEY, mod, 0)
        keyboard_ui.syn()

def type_unicode_evdev(char, layout="qwerty"):
    try:
        code = hex(ord(char))[2:]
        tap_key_with_mods(e.KEY_U, [e.KEY_LEFTCTRL, e.KEY_LEFTSHIFT])
        
        for digit in code:
            if '0' <= digit <= '9':
                k = getattr(e, f"KEY_KP{digit}")
            else:
                digit = digit.lower()
                if layout == "azerty":
                    if digit == 'a': k = e.KEY_Q
                    else: k = getattr(e, f"KEY_{digit.upper()}")
                else:
                    k = getattr(e, f"KEY_{digit.upper()}")
            tap_key(k)
            time.sleep(0.01)
        
        tap_key(e.KEY_ENTER)
    except Exception as ex:
        print(f"Evdev unicode failed: {ex}")
    time.sleep(0.01)

def type_text(text, layout="qwerty"):
    qwerty_map = {
        '!': (e.KEY_1, True), '@': (e.KEY_2, True), '#': (e.KEY_3, True), '$': (e.KEY_4, True),
        '%': (e.KEY_5, True), '^': (e.KEY_6, True), '&': (e.KEY_7, True), '*': (e.KEY_8, True),
        '(': (e.KEY_9, True), ')': (e.KEY_0, True),
        '-': (e.KEY_MINUS, False), '_': (e.KEY_MINUS, True),
        '=': (e.KEY_EQUAL, False), '+': (e.KEY_EQUAL, True),
        '[': (e.KEY_LEFTBRACE, False), '{': (e.KEY_LEFTBRACE, True),
        ']': (e.KEY_RIGHTBRACE, False), '}': (e.KEY_RIGHTBRACE, True),
        '\\': (e.KEY_BACKSLASH, False), '|': (e.KEY_BACKSLASH, True),
        ';': (e.KEY_SEMICOLON, False), ':': (e.KEY_SEMICOLON, True),
        '\'': (e.KEY_APOSTROPHE, False), '"': (e.KEY_APOSTROPHE, True),
        ',': (e.KEY_COMMA, False), '<': (e.KEY_COMMA, True),
        '.': (e.KEY_DOT, False), '>': (e.KEY_DOT, True),
        '/': (e.KEY_SLASH, False), '?': (e.KEY_SLASH, True),
        '`': (e.KEY_GRAVE, False), '~': (e.KEY_GRAVE, True)
    }

    for char in text:
        if char == ' ':
            tap_key(e.KEY_SPACE)
        elif char == '\n':
            tap_key(e.KEY_ENTER)
        elif char == '\t':
            tap_key(e.KEY_TAB)
        elif 'a' <= char.lower() <= 'z':
            k = getattr(e, f"KEY_{char.upper()}")
            if char.isupper():
                tap_key_with_mods(k, [e.KEY_LEFTSHIFT])
            else:
                tap_key(k)
        elif '0' <= char <= '9':
            k = getattr(e, f"KEY_{char}")
            tap_key(k)
        elif char in qwerty_map:
            k, shift = qwerty_map[char]
            if shift:
                tap_key_with_mods(k, [e.KEY_LEFTSHIFT])
            else:
                tap_key(k)
        else:
            try:
                res = subprocess.run(["xdotool", "type", char], capture_output=True, timeout=0.1)
                if res.returncode != 0:
                    type_unicode_evdev(char, layout)
            except Exception:
                type_unicode_evdev(char, layout)

def press_raw_key(key_name, modifiers=None):
    if not keyboard_ui:
        return
        
    key_map = {
        "BACKSPACE": e.KEY_BACKSPACE,
        "BACK": e.KEY_BACKSPACE,
        "ENTER": e.KEY_ENTER,
        "SPACE": e.KEY_SPACE,
        "TAB": e.KEY_TAB,
        "UP": e.KEY_UP,
        "DOWN": e.KEY_DOWN,
        "LEFT": e.KEY_LEFT,
        "RIGHT": e.KEY_RIGHT,
        "ESC": e.KEY_ESC,
        "DELETE": e.KEY_DELETE,
        "CTRL": e.KEY_LEFTCTRL,
        "ALT": e.KEY_LEFTALT,
        "ALTGR": e.KEY_RIGHTALT,
        "SHIFT": e.KEY_LEFTSHIFT,
        "CAPS": e.KEY_CAPSLOCK,
        "LWIN": e.KEY_LEFTMETA,
    }
    
    k = key_map.get(key_name.upper())
    if not k:
        try:
            k = getattr(e, f"KEY_{key_name.upper()}")
        except AttributeError:
            return

    if not modifiers:
        tap_key(k)
    else:
        mod_keys = []
        for mod in modifiers:
            mod_k = key_map.get(mod.upper())
            if not mod_k:
                try:
                    mod_k = getattr(e, f"KEY_{mod.upper()}")
                except AttributeError:
                    continue
            if mod_k:
                mod_keys.append(mod_k)
        
        if mod_keys:
            tap_key_with_mods(k, mod_keys)
        else:
            tap_key(k)

@keyboard_bp.route('/layout', methods=['POST'])
def set_keyboard_layout():
    try:
        data = request.json
        layout = data.get("layout", "qwerty").lower()
        global current_keyboard_layout
        current_keyboard_layout = layout
        
        if layout == "qwerty":
            subprocess.run(["gsettings", "set", "org.gnome.desktop.input-sources", "sources", "[('xkb', 'us')]"], env=get_env())
            subprocess.run(["setxkbmap", "us"])
        elif layout == "azerty":
            subprocess.run(["gsettings", "set", "org.gnome.desktop.input-sources", "sources", "[('xkb', 'fr+latin9'), ('xkb', 'fr')]"], env=get_env())
            subprocess.run(["setxkbmap", "fr"])
            
        return jsonify({"status": "success", "layout": layout}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@keyboard_bp.route('/capslock_state', methods=['GET'])
def capslock_state():
    try:
        caps_lock = False
        import glob
        for path in glob.glob('/sys/class/leds/*capslock/brightness'):
            try:
                with open(path, 'r') as f:
                    if f.read().strip() == '1':
                        caps_lock = True
                        break
            except Exception:
                continue

        if not caps_lock:
            try:
                import subprocess
                res = subprocess.run(['xset', 'q'], env=get_env(), capture_output=True, text=True, timeout=1)
                if 'Caps Lock:   on' in res.stdout:
                    caps_lock = True
            except Exception:
                pass

        if not caps_lock:
            try:
                import evdev
                for path in evdev.list_devices():
                    try:
                        dev = evdev.InputDevice(path)
                        if e.EV_LED in dev.capabilities() and e.LED_CAPSL in dev.leds(True):
                            caps_lock = True
                            break
                    except Exception:
                        continue
            except Exception:
                pass

        return jsonify({"capslock": caps_lock}), 200
    except Exception as exc:
        return jsonify({"error": str(exc)}), 500

@keyboard_bp.route('/type', methods=['POST'])
def keyboard_type():
    data = request.json
    text = data.get("text", "")
    if text:
        type_text(text, current_keyboard_layout)
    return jsonify({"status": "success"}), 200

@keyboard_bp.route('/key', methods=['POST'])
def keyboard_key():
    data = request.json
    key = data.get("key", "")
    modifiers = data.get("modifiers", [])
    if key:
        press_raw_key(key, modifiers)
    return jsonify({"status": "success"}), 200
