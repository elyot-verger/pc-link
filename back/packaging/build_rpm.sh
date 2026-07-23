#!/bin/bash
set -e

PACKAGE_NAME="pc-link-back"
VERSION="1.0.1"
RELEASE="1"

echo "Building RPM package for $PACKAGE_NAME v$VERSION..."

# Ensure rpm-build is installed
if ! command -v rpmbuild &> /dev/null; then
    echo "rpmbuild not found. Please run: sudo dnf install rpm-build"
    exit 1
fi

# Create rpmbuild directory structure
RPM_BUILD_ROOT="$HOME/rpmbuild"
mkdir -p "$RPM_BUILD_ROOT"/{BUILD,RPMS,SOURCES,SPECS,SRPMS}

# Prepare source tarball
TAR_NAME="${PACKAGE_NAME}-${VERSION}"
mkdir -p "$RPM_BUILD_ROOT/SOURCES/$TAR_NAME"
cp ../*.py "$RPM_BUILD_ROOT/SOURCES/$TAR_NAME/"
cp ../requirements.txt "$RPM_BUILD_ROOT/SOURCES/$TAR_NAME/"
cd "$RPM_BUILD_ROOT/SOURCES"
tar -czf "${TAR_NAME}.tar.gz" "$TAR_NAME"
rm -rf "$TAR_NAME"
cd - > /dev/null

# Create SPEC file
SPEC_FILE="$RPM_BUILD_ROOT/SPECS/${PACKAGE_NAME}.spec"
cat << EOF > "$SPEC_FILE"
Name:           $PACKAGE_NAME
Version:        $VERSION
Release:        $RELEASE%{?dist}
Summary:        PC-Link Backend Service
License:        GPLv3
URL:            https://github.com/elyot/pc-link
Source0:        %{name}-%{version}.tar.gz
BuildArch:      noarch

Requires:       python3 python3-flask python3-evdev playerctl upower brightnessctl ddcutil NetworkManager bluez xdotool xorg-x11-server-utils

%description
A Python backend service for remotely controlling a PC from an Android app via Wi-Fi/Tailscale. Supports both X11 and Wayland.

%prep
%autosetup

%install
rm -rf \$RPM_BUILD_ROOT

# Install python files
mkdir -p \$RPM_BUILD_ROOT/opt/pc-link-back
cp *.py \$RPM_BUILD_ROOT/opt/pc-link-back/
cp requirements.txt \$RPM_BUILD_ROOT/opt/pc-link-back/

# Install udev rules
mkdir -p \$RPM_BUILD_ROOT/etc/udev/rules.d
echo 'KERNEL=="uinput", MODE="0660", GROUP="input", OPTIONS+="static_node=uinput"' > \$RPM_BUILD_ROOT/etc/udev/rules.d/99-pc-link-uinput.rules

# Install systemd user service
mkdir -p \$RPM_BUILD_ROOT/usr/lib/systemd/user
cat << 'EOF2' > \$RPM_BUILD_ROOT/usr/lib/systemd/user/pc-link-back.service
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
EOF2

%clean
rm -rf \$RPM_BUILD_ROOT

%post
udevadm control --reload-rules || true
udevadm trigger || true
systemctl --global enable pc-link-back.service || true

%files
/opt/pc-link-back/*
/etc/udev/rules.d/99-pc-link-uinput.rules
/usr/lib/systemd/user/pc-link-back.service

%changelog
* Thu Jul 23 2026 Elyot <elyot@pclink.local> - 1.0.0-1
- Initial release
EOF

# Build RPM
rpmbuild -bb "$SPEC_FILE"

# Copy output RPM to current directory
find "$RPM_BUILD_ROOT/RPMS" -name "${PACKAGE_NAME}-${VERSION}*.rpm" -exec cp {} . \;
echo "Done! RPM package has been copied to the current directory."
