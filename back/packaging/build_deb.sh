#!/bin/bash
set -e

PACKAGE_NAME="pc-link-back"
VERSION="1.0.1"
ARCH="all"
BUILD_DIR="${PACKAGE_NAME}_${VERSION}_${ARCH}"

echo "Building Debian package for $PACKAGE_NAME v$VERSION..."

# Clean old builds
rm -rf $BUILD_DIR
rm -f ${BUILD_DIR}.deb

# Create directory structure
mkdir -p $BUILD_DIR/DEBIAN
mkdir -p $BUILD_DIR/opt/pc-link-back
mkdir -p $BUILD_DIR/usr/lib/systemd/user
mkdir -p $BUILD_DIR/etc/udev/rules.d

# Copy python files
cp ../*.py $BUILD_DIR/opt/pc-link-back/
cp ../requirements.txt $BUILD_DIR/opt/pc-link-back/

# Create udev rules
cat << 'EOF' > $BUILD_DIR/etc/udev/rules.d/99-pc-link-uinput.rules
KERNEL=="uinput", MODE="0660", GROUP="input", OPTIONS+="static_node=uinput"
EOF

# Create Systemd User Service
cat << 'EOF' > $BUILD_DIR/usr/lib/systemd/user/pc-link-back.service
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

# Create DEBIAN control file
cat << EOF > $BUILD_DIR/DEBIAN/control
Package: $PACKAGE_NAME
Version: $VERSION
Section: utils
Priority: optional
Architecture: $ARCH
Depends: python3, python3-flask, python3-evdev, playerctl, upower, brightnessctl, ddcutil, network-manager, bluez, xdotool, x11-xserver-utils
Maintainer: Elyot <elyot@pclink.local>
Description: PC-Link Backend
 A Python backend service for remotely controlling a PC (keyboard, mouse, media, status) from an Android app via Wi-Fi/Tailscale. Supports both X11 and Wayland.
EOF

# Create post-install script (postinst)
cat << 'EOF' > $BUILD_DIR/DEBIAN/postinst
#!/bin/bash
set -e
# Reload udev
udevadm control --reload-rules || true
udevadm trigger || true
systemctl --global enable pc-link-back.service || true
EOF
chmod 755 $BUILD_DIR/DEBIAN/postinst

# Build the package
dpkg-deb --build $BUILD_DIR
echo "Done! Generated ${BUILD_DIR}.deb"

# Clean up build dir
rm -rf $BUILD_DIR
