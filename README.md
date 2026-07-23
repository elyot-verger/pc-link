# PC-Link

PC-Link is an application that allows you to control your Linux PC (keyboard, mouse, media, system status) directly from your Android smartphone, via local network or Tailscale.

The project is split into two parts:
- **`android_app/`**: The client Android application.
- **`back/`**: The Python backend server that runs on the Linux PC.

---

## Prerequisites (HighlyRecommended)

To control your PC from anywhere outside your local Wi-Fi network, we highly recommend installing **Tailscale** on both your PC and your Android smartphone.
- Download it for your devices from [tailscale.com/download](https://tailscale.com/download) or via the Play Store.
- Once both devices are connected to the same Tailscale network, you can use your PC's Tailscale IP address in the Android app to connect securely from anywhere in the world.

---

## Server Installation (Linux)

The Server is designed to be easily installed on any major Linux distribution (Debian, Ubuntu, Fedora, Arch Linux, etc.) and natively supports both **X11** and **Wayland**.

### Method 1: Via Package Managers (.deb, .rpm, pacman) (Recommended)

Download the package for your distribution from the [Releases](https://github.com/elyot-verger/pc-link/releases) page of this repository.
Then follow the instructions according to your distribution:

#### 🟠 Ubuntu / Debian / Linux Mint (.deb)
```bash
sudo apt install ./pc-link-back_1.0.0_all.deb
```

#### 🔵 Fedora / RHEL / CentOS (.rpm)
```bash
sudo dnf install ./pc-link-back-1.0.0-1.noarch.rpm
```

#### 🟣 Arch Linux / Manjaro / EndeavourOS (PKGBUILD)
```bash
makepkg -si
```

---

### Method 2: Installation Script

If you download the complete source code, simply use the installation script:

```bash
cd back/packaging/
sudo ./install.sh
```

Once finished, the server will run silently in the background. (Note: A session restart may be necessary the first time so that `udev` permissions are fully applied).

---

## Android Application Installation

### Method 1: Pre-built APK (Recommended)
You can directly download the compiled `.apk` file from the **Releases** page of this repository and install it on your Android smartphone.

### Method 2: Build from Source
1. Open the `android_app/` folder with Android Studio.
2. Build the project and install the generated APK on your smartphone.

### Setup Instructions
1. Make sure your phone is connected to the same local network as your PC (or via the same Tailscale network).
2. Launch the app; the local IP address should be detected automatically or can be configured manually!

## Supported Features (for now)
- **Virtual Touchpad**: Mouse movement, left/right/middle click, scrolling, and zooming.
- **Virtual Keyboard**: Text typing, special characters, Unicode, AZERTY (FR), QWERTY (US), numpad and emoji layouts.
- **Media**: Play/Pause, Previous/Next, lyrics retrieval (via lrclib).
- **System Status**: Battery management, volume (Mute/Up/Down), power profiles, monitors, Bluetooth, and Wi-Fi toggles, distant locking.

## Future Features (planned)
- **Virtual Microphone**
- **Virtual Camera**
- **Virtual Screen**
- **File Sharing**