import time
import subprocess
from flask import Blueprint, jsonify, request
from device_manager import mouse_ui, keyboard_ui
try:
    from evdev import ecodes as e
except ImportError:
    pass

touchpad_bp = Blueprint('touchpad_bp', __name__)

frac_x = 0.0
frac_y = 0.0

def move_mouse(dx, dy):
    global frac_x, frac_y
    if mouse_ui:
        frac_x += dx
        frac_y += dy
        
        move_x = int(frac_x)
        move_y = int(frac_y)
        
        if move_x != 0 or move_y != 0:
            mouse_ui.write(e.EV_REL, e.REL_X, move_x)
            mouse_ui.write(e.EV_REL, e.REL_Y, move_y)
            mouse_ui.syn()
            
            frac_x -= move_x
            frac_y -= move_y

def scroll_mouse(dy):
    if mouse_ui:
        mouse_ui.write(e.EV_REL, e.REL_WHEEL, int(dy))
        mouse_ui.syn()

def click_mouse(button):
    if mouse_ui:
        btn_code = e.BTN_LEFT
        if button == 'right':
            btn_code = e.BTN_RIGHT
        elif button == 'middle':
            btn_code = e.BTN_MIDDLE
            
        mouse_ui.write(e.EV_KEY, btn_code, 1)
        mouse_ui.syn()
        time.sleep(0.05)
        mouse_ui.write(e.EV_KEY, btn_code, 0)
        mouse_ui.syn()

def mouse_down(button):
    if mouse_ui:
        btn_code = e.BTN_LEFT
        if button == 'right':
            btn_code = e.BTN_RIGHT
        elif button == 'middle':
            btn_code = e.BTN_MIDDLE
        mouse_ui.write(e.EV_KEY, btn_code, 1)
        mouse_ui.syn()

def mouse_up(button):
    if mouse_ui:
        btn_code = e.BTN_LEFT
        if button == 'right':
            btn_code = e.BTN_RIGHT
        elif button == 'middle':
            btn_code = e.BTN_MIDDLE
        mouse_ui.write(e.EV_KEY, btn_code, 0)
        mouse_ui.syn()

def zoom(dy):
    if mouse_ui and keyboard_ui:
        keyboard_ui.write(e.EV_KEY, e.KEY_LEFTCTRL, 1)
        keyboard_ui.syn()
        time.sleep(0.01)
        mouse_ui.write(e.EV_REL, e.REL_WHEEL, int(dy))
        mouse_ui.syn()
        time.sleep(0.01)
        keyboard_ui.write(e.EV_KEY, e.KEY_LEFTCTRL, 0)
        keyboard_ui.syn()

def trigger_gesture(gesture):
    if not keyboard_ui:
        return
        
    if gesture == 'up':
        keyboard_ui.write(e.EV_KEY, e.KEY_LEFTMETA, 1)
        keyboard_ui.syn()
        time.sleep(0.05)
        keyboard_ui.write(e.EV_KEY, e.KEY_LEFTMETA, 0)
        keyboard_ui.syn()
        subprocess.run(["dbus-send", "--session", "--type=method_call", "--dest=org.gnome.Shell", "/org/gnome/Shell", "org.freedesktop.DBus.Properties.Set", "string:org.gnome.Shell", "string:OverviewActive", "variant:boolean:true"], capture_output=True)
    elif gesture == 'left':
        keyboard_ui.write(e.EV_KEY, e.KEY_LEFTCTRL, 1)
        keyboard_ui.write(e.EV_KEY, e.KEY_LEFTALT, 1)
        keyboard_ui.syn()
        time.sleep(0.02)
        keyboard_ui.write(e.EV_KEY, e.KEY_LEFT, 1)
        keyboard_ui.syn()
        time.sleep(0.05)
        keyboard_ui.write(e.EV_KEY, e.KEY_LEFT, 0)
        keyboard_ui.write(e.EV_KEY, e.KEY_LEFTALT, 0)
        keyboard_ui.write(e.EV_KEY, e.KEY_LEFTCTRL, 0)
        keyboard_ui.syn()
    elif gesture == 'right':
        keyboard_ui.write(e.EV_KEY, e.KEY_LEFTCTRL, 1)
        keyboard_ui.write(e.EV_KEY, e.KEY_LEFTALT, 1)
        keyboard_ui.syn()
        time.sleep(0.02)
        keyboard_ui.write(e.EV_KEY, e.KEY_RIGHT, 1)
        keyboard_ui.syn()
        time.sleep(0.05)
        keyboard_ui.write(e.EV_KEY, e.KEY_RIGHT, 0)
        keyboard_ui.write(e.EV_KEY, e.KEY_LEFTALT, 0)
        keyboard_ui.write(e.EV_KEY, e.KEY_LEFTCTRL, 0)
        keyboard_ui.syn()

@touchpad_bp.route('/move', methods=['POST'])
def route_mouse_move():
    try:
        data = request.json
        dx = data.get('dx', 0)
        dy = data.get('dy', 0)
        move_mouse(dx, dy)
        return jsonify({"status": "success"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@touchpad_bp.route('/scroll', methods=['POST'])
def route_mouse_scroll():
    try:
        data = request.json
        dy = data.get('dy', 0)
        scroll_mouse(dy)
        return jsonify({"status": "success"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@touchpad_bp.route('/click', methods=['POST'])
def route_mouse_click():
    try:
        data = request.json
        button = data.get('button', 'left')
        click_mouse(button)
        return jsonify({"status": "success"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500
