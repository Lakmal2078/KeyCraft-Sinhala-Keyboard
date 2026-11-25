"""Exercise the signed release using a separately debug-signed test harness."""

from pathlib import Path
import subprocess
import sys

PACKAGE = "com.lakmal.keycraftsinhala"
IME = f"{PACKAGE}/.Keyboard2"
RUNNER = f"{PACKAGE}.debug.test/androidx.test.runner.AndroidJUnitRunner"


def adb(*args):
    return subprocess.check_output(("adb", *args), text=True, timeout=120).strip()


def install(apk):
    if "Success" not in adb("install", "-t", str(apk)).splitlines():
        raise RuntimeError(f"APK installation failed: {apk}")


def instrument(test):
    result = adb("shell", "am", "instrument", "-w", "-e", "class",
                 f"{PACKAGE}.{test}", "-e", "signedReleaseSmoke", "true", RUNNER)
    print(result, flush=True)
    if "OK (1 test)" not in result:
        raise RuntimeError(f"Instrumentation failed: {test}")


def main(apk, harness):
    print("Android API:", adb("shell", "getprop", "ro.build.version.sdk"), flush=True)
    install(apk)
    for variant in ("debug", "androidTest/debug"):
        (harness_apk,) = (harness / variant).glob("*.apk")
        install(harness_apk)

    instrument("PinnedClipboardStoreTest")
    adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
    adb("shell", "ime", "enable", IME)
    adb("shell", "ime", "set", IME)
    current = adb("shell", "settings", "get", "secure", "default_input_method")
    if current not in (IME, f"{PACKAGE}/{PACKAGE}.Keyboard2"):
        raise RuntimeError("Release IME was not selected")
    launch = adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.LauncherActivity")
    if "Status: ok" not in launch:
        raise RuntimeError(launch)
    instrument("SignedReleaseSmokeTest")


if __name__ == "__main__":
    main(sys.argv[1], Path(sys.argv[2]))
