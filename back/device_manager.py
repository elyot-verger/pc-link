import os
import subprocess

# Shared virtual devices for Keyboard and Touchpad
keyboard_ui = None
mouse_ui = None

try:
    import evdev
    from evdev import UInput, ecodes as e
    keyboard_ui = UInput(name="DistantLockButton Virtual Keyboard", vendor=0x045e, product=0x028e, version=0x0111, bustype=e.BUS_USB)
    
    mouse_cap = {
        e.EV_KEY: [e.BTN_LEFT, e.BTN_RIGHT, e.BTN_MIDDLE],
        e.EV_REL: [e.REL_X, e.REL_Y, e.REL_WHEEL]
    }
    mouse_ui = UInput(mouse_cap, name="DistantLockButton Virtual Mouse", vendor=0x045e, product=0x028e, version=0x0111, bustype=e.BUS_USB)
except Exception as exc:
    print(f"Failed to init UInput: {exc}")

def get_env():
    env = os.environ.copy()
    try:
        res = subprocess.run(["systemctl", "--user", "show-environment"], capture_output=True, text=True)
        for line in res.stdout.split('\n'):
            if '=' in line:
                key, val = line.split('=', 1)
                env[key] = val
    except Exception:
        pass

    uid = os.getuid()
    if 'XDG_RUNTIME_DIR' not in env:
        env['XDG_RUNTIME_DIR'] = f"/run/user/{uid}"
    if 'DISPLAY' not in env:
        env['DISPLAY'] = ":0"
    if 'WAYLAND_DISPLAY' not in env:
        env['WAYLAND_DISPLAY'] = "wayland-0"
    if 'DBUS_SESSION_BUS_ADDRESS' not in env:
        env['DBUS_SESSION_BUS_ADDRESS'] = f"unix:path=/run/user/{uid}/bus"
    return env
