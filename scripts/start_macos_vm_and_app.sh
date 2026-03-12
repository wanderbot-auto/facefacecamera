#!/bin/zsh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SCRIPT_NAME="$(basename "$0")"

DEFAULT_ANDROID_HOME="${HOME}/Library/Android/sdk"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$DEFAULT_ANDROID_HOME}}"
export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"

OPENJDK17_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
if [[ -z "${JAVA_HOME:-}" && -d "$OPENJDK17_HOME" ]]; then
  export JAVA_HOME="$OPENJDK17_HOME"
fi

export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:/opt/homebrew/opt/openjdk@17/bin:$PATH"

APP_ID="com.facefacecamera"
MAIN_ACTIVITY="com.facefacecamera.MainActivity"
AVD_NAME="${FFC_AVD_NAME:-FaceFaceCamera_API_35}"
SYSTEM_IMAGE="${FFC_SYSTEM_IMAGE:-system-images;android-35;google_apis;arm64-v8a}"
BUILD_TOOLS="${FFC_BUILD_TOOLS:-build-tools;35.0.0}"
PLATFORM="${FFC_PLATFORM:-platforms;android-35}"
DEVICE_ID="${FFC_DEVICE_ID:-pixel_8}"
EMULATOR_ARGS="${FFC_EMULATOR_ARGS:--netdelay none -netspeed full}"
SYSTEM_IMAGE_EXPLICIT=0

usage() {
  cat <<EOF
Usage: $SCRIPT_NAME [options]

Starts an Android emulator on macOS, installs the debug app, and launches it.

Options:
  --avd-name NAME         Override the default AVD name ($AVD_NAME)
  --system-image ID       Override the default system image ($SYSTEM_IMAGE)
  --device-id ID          Override the default device profile ($DEVICE_ID)
  --skip-build            Skip ./gradlew installDebug
  --skip-sdk-install      Skip sdkmanager installs and assume SDK packages already exist
  --cold-boot             Start emulator with -no-snapshot-load
  --help                  Show this help

Environment overrides:
  FFC_AVD_NAME
  FFC_SYSTEM_IMAGE
  FFC_DEVICE_ID
  FFC_BUILD_TOOLS
  FFC_PLATFORM
  FFC_EMULATOR_ARGS
EOF
}

SKIP_BUILD=0
SKIP_SDK_INSTALL=0
COLD_BOOT=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --avd-name)
      AVD_NAME="$2"
      shift 2
      ;;
    --system-image)
      SYSTEM_IMAGE="$2"
      SYSTEM_IMAGE_EXPLICIT=1
      shift 2
      ;;
    --device-id)
      DEVICE_ID="$2"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --skip-sdk-install)
      SKIP_SDK_INSTALL=1
      shift
      ;;
    --cold-boot)
      COLD_BOOT=1
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

require_command() {
  local command_name="$1"
  local hint="$2"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Missing command: $command_name" >&2
    echo "$hint" >&2
    exit 1
  fi
}

require_path() {
  local path_value="$1"
  local hint="$2"
  if [[ ! -e "$path_value" ]]; then
    echo "Missing path: $path_value" >&2
    echo "$hint" >&2
    exit 1
  fi
}

log() {
  printf "\n[%s] %s\n" "$(date '+%H:%M:%S')" "$1" >&2
}

system_image_relpath() {
  local package_id="$1"
  printf '%s/\n' "$(echo "$package_id" | tr ';' '/')"
}

system_image_api() {
  local package_id="$1"
  local parts=("${(@s/;/)package_id}")
  echo "${parts[2]}"
}

system_image_tag() {
  local package_id="$1"
  local parts=("${(@s/;/)package_id}")
  echo "${parts[3]}"
}

system_image_abi() {
  local package_id="$1"
  local parts=("${(@s/;/)package_id}")
  echo "${parts[4]}"
}

system_image_tag_display() {
  case "$(system_image_tag "$1")" in
    google_apis_playstore) echo "Google Play" ;;
    google_apis) echo "Google APIs" ;;
    aosp_atd) echo "AOSP ATD" ;;
    google_atd) echo "Google ATD" ;;
    *) echo "$(system_image_tag "$1")" ;;
  esac
}

installed_packages() {
  sdkmanager --sdk_root="$ANDROID_HOME" --list_installed 2>/dev/null | awk -F'|' 'NR > 4 { gsub(/^[ \t]+|[ \t]+$/, "", $1); if ($1 != "") print $1 }'
}

is_avd_compatible_system_image() {
  local package_id="$1"
  [[ "$package_id" =~ ^system-images\;android-[0-9]+\; ]]
}

is_package_installed() {
  local package_id="$1"
  installed_packages | grep -Fxq "$package_id"
}

pick_installed_system_image() {
  installed_packages | while IFS= read -r package_id; do
    if is_avd_compatible_system_image "$package_id"; then
      echo "$package_id"
      break
    fi
  done
}

resolve_system_image() {
  if [[ "$SYSTEM_IMAGE_EXPLICIT" -eq 1 ]]; then
    log "Using explicitly requested system image: $SYSTEM_IMAGE"
    return
  fi

  if is_package_installed "$SYSTEM_IMAGE"; then
    log "Using preferred system image: $SYSTEM_IMAGE"
    return
  fi

  local installed_image
  installed_image="$(pick_installed_system_image || true)"
  if [[ -n "$installed_image" ]]; then
    SYSTEM_IMAGE="$installed_image"
    log "Preferred system image is not installed; reusing installed image: $SYSTEM_IMAGE"
    return
  fi

  local incompatible_installed_image
  incompatible_installed_image="$(installed_packages | awk '/^system-images;/ { print; exit }' || true)"
  if [[ -n "$incompatible_installed_image" ]]; then
    log "Installed system image is not AVD-compatible with avdmanager: $incompatible_installed_image"
  fi

  log "No installed system image found; will install: $SYSTEM_IMAGE"
}

ensure_sdk_packages() {
  if [[ "$SKIP_SDK_INSTALL" -eq 1 ]]; then
    return
  fi

  require_command sdkmanager "Install Android command-line tools first."

  local desired_packages=(
    "platform-tools"
    "$PLATFORM"
    "$BUILD_TOOLS"
    "emulator"
    "$SYSTEM_IMAGE"
  )
  local missing_packages=()
  local package_id
  for package_id in "${desired_packages[@]}"; do
    if ! is_package_installed "$package_id"; then
      missing_packages+=("$package_id")
    fi
  done

  if [[ ${#missing_packages[@]} -eq 0 ]]; then
    log "All required Android SDK packages are already installed"
    return
  fi

  log "Installing missing Android SDK packages:"
  printf '  - %s\n' "${missing_packages[@]}"
  yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null
  sdkmanager --sdk_root="$ANDROID_HOME" "${missing_packages[@]}"
}

ensure_avd() {
  require_command avdmanager "Android cmdline tools are required to create an AVD."
  local avd_root="${HOME}/.android/avd"
  local avd_dir="${avd_root}/${AVD_NAME}.avd"
  local avd_ini="${avd_root}/${AVD_NAME}.ini"

  if [[ -f "$avd_ini" && -d "$avd_dir" ]]; then
    log "Using existing AVD: $AVD_NAME"
    return
  fi

  log "Creating AVD: $AVD_NAME"
  mkdir -p "$avd_root"
  if printf 'no\n' | avdmanager create avd \
    --force \
    --name "$AVD_NAME" \
    --package "$SYSTEM_IMAGE" \
    --device "$DEVICE_ID"; then
    return
  fi

  log "avdmanager failed; falling back to manual AVD config generation"
  create_manual_avd
}

device_profile() {
  case "$1" in
    pixel_8_pro)
      cat <<EOF
hw.device.name=pixel_8_pro
hw.device.manufacturer=Google
hw.lcd.width=1344
hw.lcd.height=2992
hw.lcd.density=489
hw.ramSize=2048
vm.heapSize=256
skin.name=1344x2992
EOF
      ;;
    pixel_8|xiaomi13|xiaomi_13)
      cat <<EOF
hw.device.name=$1
hw.device.manufacturer=Generic
hw.lcd.width=1080
hw.lcd.height=2400
hw.lcd.density=420
hw.ramSize=1536
vm.heapSize=256
skin.name=1080x2400
EOF
      ;;
    pixel_7_pro)
      cat <<EOF
hw.device.name=pixel_7_pro
hw.device.manufacturer=Google
hw.lcd.width=1440
hw.lcd.height=3120
hw.lcd.density=560
hw.ramSize=2048
vm.heapSize=256
skin.name=1440x3120
EOF
      ;;
    pixel_7)
      cat <<EOF
hw.device.name=pixel_7
hw.device.manufacturer=Google
hw.lcd.width=1080
hw.lcd.height=2400
hw.lcd.density=420
hw.ramSize=1536
vm.heapSize=256
skin.name=1080x2400
EOF
      ;;
    *)
      cat <<EOF
hw.device.name=medium_phone
hw.device.manufacturer=Generic
hw.lcd.width=1080
hw.lcd.height=2400
hw.lcd.density=420
hw.ramSize=1536
vm.heapSize=256
skin.name=1080x2400
EOF
      ;;
  esac
}

create_manual_avd() {
  local avd_root="${HOME}/.android/avd"
  local avd_dir="${avd_root}/${AVD_NAME}.avd"
  local avd_ini="${avd_root}/${AVD_NAME}.ini"
  local relpath
  relpath="$(system_image_relpath "$SYSTEM_IMAGE")"
  local target
  target="$(system_image_api "$SYSTEM_IMAGE")"
  local tag
  tag="$(system_image_tag "$SYSTEM_IMAGE")"
  local tag_display
  tag_display="$(system_image_tag_display "$SYSTEM_IMAGE")"
  local abi
  abi="$(system_image_abi "$SYSTEM_IMAGE")"
  local playstore_enabled="false"
  if [[ "$tag" == "google_apis_playstore" ]]; then
    playstore_enabled="true"
  fi

  mkdir -p "$avd_dir"

  cat >"$avd_ini" <<EOF
avd.ini.encoding=UTF-8
path=${avd_dir}
path.rel=avd/${AVD_NAME}.avd
target=${target}
EOF

  cat >"${avd_dir}/config.ini" <<EOF
AvdId=${AVD_NAME}
PlayStore.enabled=${playstore_enabled}
abi.type=${abi}
avd.ini.displayname=${AVD_NAME}
avd.ini.encoding=UTF-8
disk.dataPartition.size=6G
fastboot.forceChosenSnapshotBoot=no
fastboot.forceColdBoot=no
fastboot.forceFastBoot=yes
hw.accelerometer=yes
hw.arc=false
hw.audioInput=yes
hw.battery=yes
hw.camera.back=virtualscene
hw.camera.front=emulated
hw.cpu.arch=arm64
hw.cpu.ncore=2
hw.dPad=no
hw.gps=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.gyroscope=yes
hw.initialOrientation=portrait
hw.keyboard=yes
hw.mainKeys=no
hw.sdCard=no
hw.sensors.light=yes
hw.sensors.magnetic_field=yes
hw.sensors.orientation=yes
hw.sensors.pressure=yes
hw.sensors.proximity=yes
hw.trackBall=no
image.sysdir.1=${relpath}
runtime.network.latency=none
runtime.network.speed=full
showDeviceFrame=yes
skin.dynamic=yes
tag.display=${tag_display}
tag.id=${tag}
target=${target}
$(device_profile "$DEVICE_ID")
EOF
}

find_running_emulator_serial() {
  adb devices | awk 'NR > 1 && $2 == "device" && $1 ~ /^emulator-/ { print $1; exit }'
}

start_emulator() {
  local emulator_bin="$ANDROID_HOME/emulator/emulator"
  require_path "$emulator_bin" "Install the Android emulator package first."

  local serial
  serial="$(find_running_emulator_serial || true)"
  if [[ -n "$serial" ]]; then
    log "Using running emulator: $serial"
    echo "$serial"
    return
  fi

  log "Starting emulator: $AVD_NAME"
  local log_file="$PROJECT_ROOT/.emulator.log"
  local extra_args=()
  if [[ "$COLD_BOOT" -eq 1 ]]; then
    extra_args+=("-no-snapshot-load")
  fi

  nohup "$emulator_bin" -avd "$AVD_NAME" "${extra_args[@]}" ${(z)EMULATOR_ARGS} >"$log_file" 2>&1 &

  local attempts=0
  while [[ $attempts -lt 60 ]]; do
    serial="$(find_running_emulator_serial || true)"
    if [[ -n "$serial" ]]; then
      echo "$serial"
      return
    fi
    attempts=$((attempts + 1))
    sleep 2
  done

  echo "Timed out waiting for emulator to register with adb. See $log_file" >&2
  exit 1
}

wait_for_boot() {
  local serial="$1"
  log "Waiting for emulator boot completion"
  adb -s "$serial" wait-for-device >/dev/null

  local boot_completed=""
  local attempts=0
  while [[ $attempts -lt 180 ]]; do
    boot_completed="$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    if [[ "$boot_completed" == "1" ]]; then
      adb -s "$serial" shell input keyevent 82 >/dev/null 2>&1 || true
      return
    fi
    attempts=$((attempts + 1))
    sleep 2
  done

  echo "Timed out waiting for Android to finish booting." >&2
  exit 1
}

build_and_install() {
  if [[ "$SKIP_BUILD" -eq 1 ]]; then
    log "Skipping app build/install"
    return
  fi

  log "Building and installing debug app"
  (cd "$PROJECT_ROOT" && ./gradlew installDebug)
}

launch_app() {
  local serial="$1"
  log "Launching app"
  adb -s "$serial" shell am start -n "$APP_ID/$MAIN_ACTIVITY" >/dev/null
}

main() {
  require_command adb "Android platform-tools are required."
  require_path "$PROJECT_ROOT/gradlew" "Run this script from the FaceFaceCamera project checkout."

  resolve_system_image
  ensure_sdk_packages
  ensure_avd
  local serial
  serial="$(start_emulator)"
  wait_for_boot "$serial"
  build_and_install
  launch_app "$serial"
  log "Done. Emulator serial: $serial"
}

main
