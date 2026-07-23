#!/bin/bash
set -e

echo "================================================"
echo "    PC-Link Backend Universal Installer         "
echo "    Supports: X11 & Wayland                     "
echo "================================================"

if [ "$EUID" -ne 0 ]; then
  echo "Please run this script as root (sudo ./install.sh)"
  exit 1
fi

REAL_USER=${SUDO_USER:-$(who am i | awk '{print $1}')}
if [ -z "$REAL_USER" ] || [ "$REAL_USER" == "root" ]; then
    echo "Could not determine the real user. Please run via sudo."
    exit 1
fi

echo "[1/5] Detecting package manager & installing dependencies..."
if command -v apt-get &> /dev/null; then
    apt-get update
    apt-get install -y python3 python3-flask python3-evdev playerctl upower brightnessctl ddcutil network-manager bluez xdotool x11-xserver-utils
elif command -v dnf &> /dev/null; then
    dnf install -y python3 python3-flask python3-evdev playerctl upower brightnessctl ddcutil NetworkManager bluez xdotool xorg-x11-server-utils
elif command -v pacman &> /dev/null; then
    pacman -Sy --noconfirm python python-flask python-evdev playerctl upower brightnessctl ddcutil networkmanager bluez xdotool xorg-xset xorg-xrandr
else
    echo "Unsupported package manager. Please install dependencies manually:"
    echo "python3, flask, evdev, playerctl, upower, brightnessctl, ddcutil, networkmanager, bluez, xdotool, xset, xrandr"
fi

echo "[2/5] Installing application files to /opt/pc-link-back..."
mkdir -p /opt/pc-link-back
cp -r ../*.py /opt/pc-link-back/
cp -r ../requirements.txt /opt/pc-link-back/
chown -R $REAL_USER:$REAL_USER /opt/pc-link-back

echo "[3/5] Setting up udev rules for Wayland/X11 virtual input (evdev)..."
cat << 'EOF' > /etc/udev/rules.d/99-pc-link-uinput.rules
KERNEL=="uinput", MODE="0660", GROUP="input", OPTIONS+="static_node=uinput"
EOF
usermod -aG input $REAL_USER
udevadm control --reload-rules
udevadm trigger

echo "[4/5] Setting up Systemd User Service..."
SERVICE_DIR="/usr/lib/systemd/user"
mkdir -p $SERVICE_DIR

cat << EOF > $SERVICE_DIR/pc-link-back.service
[Unit]
Description=PC-Link Backend Service
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/python3 /opt/pc-link-back/app.py
Restart=on-failure
RestartSec=5

[Install]
WantedBy=default.target
EOF

systemctl daemon-reload

echo "[5/5] Enabling and starting the service for user $REAL_USER..."
# Execute as the real user to enable the user-level systemd service
su - $REAL_USER -c "systemctl --user daemon-reload"
su - $REAL_USER -c "systemctl --user enable pc-link-back.service"
su - $REAL_USER -c "systemctl --user start pc-link-back.service"

echo "================================================"
echo " Installation Complete! "
echo " The backend is now running as a background service."
echo " Note: You may need to restart your computer or log out and log back in"
echo "       for the 'input' group permissions (udev rules) to apply fully to your user."
echo "================================================"
