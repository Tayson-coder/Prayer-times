import os
import subprocess
import sys
import tarfile
import threading
import time
import tkinter as tk
import tkinter.font as tkfont
from tkinter import ttk, scrolledtext, messagebox, filedialog

APP_NAME = "ZG"
APP_TAGLINE = "Android Service Tool"

# ---- palette (bench service equipment: graphite chassis + signal accent) ----
CHASSIS = "#20242B"
CHASSIS_LINE = "#2E333C"
PANEL = "#EDEFF2"
CARD = "#FFFFFF"
INK = "#1B1F26"
MUTED = "#6B7480"
LINE = "#D5D9DF"
ACCENT = "#F0641E"
ACCENT_DK = "#CF4F12"
DANGER = "#D23B3B"
DANGER_DK = "#B52E2E"
OK = "#2E9E6B"
CONSOLE_BG = "#12161C"
CONSOLE_FG = "#CFD6DE"
CONSOLE_DIM = "#7E8894"
ON_DARK = "#F2F4F7"


def base_dir():
    if getattr(sys, "frozen", False):
        return getattr(sys, "_MEIPASS", os.path.dirname(sys.executable))
    return os.path.dirname(os.path.abspath(__file__))


def exe_dir():
    if getattr(sys, "frozen", False):
        return os.path.dirname(sys.executable)
    return os.path.dirname(os.path.abspath(__file__))


def tool_path(name):
    """Find a tool next to the app (or in platform-tools), else on PATH."""
    exe = name + (".exe" if os.name == "nt" else "")
    for c in (os.path.join(exe_dir(), exe), os.path.join(exe_dir(), "platform-tools", exe)):
        if os.path.isfile(c):
            return c
    return name


ADB = tool_path("adb")
FASTBOOT = tool_path("fastboot")
HEIMDALL = tool_path("heimdall")
SCRCPY = tool_path("scrcpy")
NO_WINDOW = 0x08000000 if os.name == "nt" else 0
MISSING_TOOL = "Tool not found. Put adb.exe and fastboot.exe next to this app."

KNOWN_PARTITIONS = {
    "sboot": "BOOTLOADER", "boot": "BOOT", "init_boot": "INIT_BOOT", "vendor_boot": "VENDOR_BOOT",
    "recovery": "RECOVERY", "system": "SYSTEM", "super": "SUPER", "userdata": "USERDATA",
    "cache": "CACHE", "modem": "RADIO", "cp": "RADIO", "vbmeta": "VBMETA", "vbmeta_system": "VBMETA_SYSTEM",
    "dtbo": "DTBO", "dtb": "DTB", "vendor": "VENDOR", "up_param": "UP_PARAM", "param": "PARAM",
    "keystorage": "KEYSTORAGE", "uh": "UH", "cm": "CM", "optics": "OPTICS", "prism": "PRISM",
}
IMAGE_EXT = (".img", ".bin", ".img.lz4", ".bin.lz4", ".mbn", ".pit")
FB_PARTITIONS = ["boot", "init_boot", "recovery", "vbmeta", "vbmeta_system", "dtbo",
                 "vendor_boot", "system", "vendor", "product", "super", "modem",
                 "radio", "dtb", "cache", "metadata", "userdata"]


def run(cmd, timeout=20):
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout,
                           creationflags=NO_WINDOW, encoding="utf-8", errors="replace")
        return (r.stdout + r.stderr).strip()
    except FileNotFoundError:
        return "ERROR: " + MISSING_TOOL
    except subprocess.TimeoutExpired:
        return "ERROR: Timed out (device not responding)."


def run_bytes(cmd, timeout=30):
    try:
        r = subprocess.run(cmd, capture_output=True, timeout=timeout, creationflags=NO_WINDOW)
        if r.returncode != 0:
            return None, r.stderr.decode("utf-8", "replace").strip() or "Command failed."
        return r.stdout, None
    except FileNotFoundError:
        return None, MISSING_TOOL
    except subprocess.TimeoutExpired:
        return None, "Timed out (device not responding)."


def run_stream(cmd, on_line, timeout=1800):
    """Run a long command (e.g. flashing) and emit each line as it arrives. Returns exit code."""
    try:
        p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                             encoding="utf-8", errors="replace", creationflags=NO_WINDOW, bufsize=1)
    except FileNotFoundError:
        on_line(MISSING_TOOL)
        return -1
    start = time.time()
    for line in p.stdout:
        on_line(line.rstrip("\n"))
        if time.time() - start > timeout:
            p.kill()
            on_line("ERROR: Timed out.")
            return -1
    return p.wait()


# ---------------- device discovery ----------------
def parse_adb_devices(output):
    devices = []
    for line in output.splitlines():
        line = line.strip()
        if not line or line.startswith(("List of devices", "*", "ERROR", "adb:", "error:")):
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        serial, state = parts[0], parts[1]
        model = ""
        for p in parts[2:]:
            if p.startswith("model:"):
                model = p.split(":", 1)[1].replace("_", " ")
        devices.append((serial, state, model))
    return devices


def parse_fastboot_devices(output):
    serials = []
    for line in output.splitlines():
        parts = line.split()
        if len(parts) >= 2 and parts[1] in ("fastboot", "fastbootd"):
            serials.append(parts[0])
    return serials


def adb_ready(state_output):
    """Device is usable only when the reply is exactly 'device'.
    (Error strings like 'error: device unauthorized' and 'no devices/emulators found'
    also contain the word 'device', so we must not substring-match it.)"""
    return state_output.strip() == "device"


STATE_HELP = {
    "unauthorized": "Device refused the connection: unlock the screen and accept the RSA prompt (Allow USB debugging).",
    "offline": "Device is offline: unplug and replug, or run 'adb kill-server'.",
    "recovery": "Device is in recovery mode.",
    "sideload": "Device is in sideload mode.",
}


def explain_state(state_output):
    s = state_output.strip()
    for key, msg in STATE_HELP.items():
        if key in s:
            return msg
    if "no devices" in s or "not found" in s:
        return "No device on adb. Check the cable and that USB debugging is on."
    if "more than one" in s:
        return "More than one device: pick one from the list above."
    return s or "No reply from adb."


# ---------------- Samsung firmware (tar.md5 / PIT) ----------------
def list_tar_images(path):
    names = []
    with tarfile.open(path, "r:*") as tf:
        for m in tf.getmembers():
            if m.isfile() and m.name.lower().endswith(IMAGE_EXT):
                names.append((m.name, m.size))
    return names


def extract_tar_images(path, dest):
    os.makedirs(dest, exist_ok=True)
    out = []
    with tarfile.open(path, "r:*") as tf:
        for m in tf.getmembers():
            if not (m.isfile() and m.name.lower().endswith(IMAGE_EXT)):
                continue
            target = os.path.join(dest, os.path.basename(m.name))
            with tf.extractfile(m) as src, open(target, "wb") as f:
                while True:
                    chunk = src.read(1024 * 1024)
                    if not chunk:
                        break
                    f.write(chunk)
            out.append(target)
    return out


def guess_partition(fname):
    base = os.path.basename(fname)
    for ext in (".img.lz4", ".bin.lz4", ".img", ".bin", ".mbn", ".pit"):
        if base.lower().endswith(ext):
            base = base[: -len(ext)]
            break
    return KNOWN_PARTITIONS.get(base.lower(), base.upper())


def parse_pit_partitions(text):
    return [line.split(":", 1)[1].strip()
            for line in text.splitlines() if line.strip().startswith("Partition Name:")]


def build_flash_cmd(mapping, pit_path=None, repartition=False, no_reboot=False):
    cmd = [HEIMDALL, "flash"]
    if repartition and pit_path:
        cmd += ["--repartition", "--pit", pit_path]
    for part, path in mapping:
        cmd += [f"--{part}", path]
    if no_reboot:
        cmd += ["--no-reboot"]
    return cmd


# ---------------- MTP read (Windows only) ----------------
MTP_SETUP_HELP = (
    "To read over MTP you need two things, once:\n"
    "1) pip install pythonnet\n"
    "2) MediaDevices.dll: download the 'MediaDevices' package from nuget.org,\n"
    "   rename the .nupkg to .zip, open it, copy MediaDevices.dll from the lib\n"
    "   folder, and place it next to this app.\n"
    "   (Right-click the DLL > Properties > tick Unblock if shown.)"
)


def fmt_size(n):
    try:
        n = float(n)
    except (TypeError, ValueError):
        return str(n)
    return f"{n / (1024 ** 3):.2f} GB"


def safe_get(obj, attr):
    try:
        val = getattr(obj, attr)
    except Exception:
        return None
    if val is None or str(val).strip() == "":
        return None
    return val


def read_mtp_devices():
    if os.name != "nt":
        return "MTP reading works on Windows only."
    try:
        import clr  # pythonnet
    except ImportError:
        return "pythonnet is not installed.\n" + MTP_SETUP_HELP
    dll = next((os.path.join(d, "MediaDevices.dll") for d in (exe_dir(), base_dir())
                if os.path.isfile(os.path.join(d, "MediaDevices.dll"))), None)
    if not dll:
        return "MediaDevices.dll is not next to the app.\n" + MTP_SETUP_HELP
    try:
        clr.AddReference(dll)
        from MediaDevices import MediaDevice
    except Exception as e:
        return f"Could not load MediaDevices.dll: {e}\n" + MTP_SETUP_HELP
    try:
        devices = list(MediaDevice.GetDevices())
    except Exception as e:
        return f"MTP search failed: {e}"
    if not devices:
        return ("No phone in MTP mode.\n"
                "- Unlock the screen.\n"
                "- From the USB notification pick 'File transfer'.\n"
                "- Try another cable.")
    lines = []
    for idx, d in enumerate(devices, 1):
        lines.append("------------------------")
        lines.append(f"MTP device {idx}")
        try:
            d.Connect()
        except Exception as e:
            lines.append(f"Connection failed: {e}")
            continue
        try:
            fields = [("Name", "FriendlyName"), ("Description", "Description"), ("Manufacturer", "Manufacturer"),
                      ("Model", "Model"), ("Serial", "SerialNumber"), ("Firmware", "FirmwareVersion"),
                      ("Type", "DeviceType"), ("Protocol", "Protocol"),
                      ("Power source", "PowerSource"), ("Battery", "PowerLevel")]
            for label, attr in fields:
                val = safe_get(d, attr)
                lines.append(f"{label}: {val if val is not None else '- (not reported)'}")
            try:
                drives = list(d.GetDrives() or [])
            except Exception:
                drives = []
            for dr in drives:
                name = safe_get(dr, "Name") or safe_get(dr, "Description") or "Storage"
                total = safe_get(dr, "TotalSize")
                free = safe_get(dr, "AvailableFreeSpace") or safe_get(dr, "FreeSpaceInBytes")
                lines.append(f"Storage [{name}]: total {fmt_size(total) if total is not None else '-'}"
                             f" | free {fmt_size(free) if free is not None else '-'}")
        finally:
            try:
                d.Disconnect()
            except Exception:
                pass
    return "\n".join(lines)


# ---------------- Exynos EUB emergency bootloader upload (unbrick) ----------------
# Reimplements the open-source SMDK USB download protocol (Ben Dooks' smdk-usbdl,
# adapted for Exynos9610 / Galaxy A50 / A505 by astarasikov). It UPLOADS a
# bootloader to a device that is ALREADY in EUB (emergency USB boot) mode, to
# recover a brick. It does NOT put a device into EUB and does NOT bypass any lock.
EUB_VID = 0x04E8
EUB_PID = 0x1234
EUB_OUT_EP = 0x02
EUB_SEQUENCE = [1, 2, 3, 1, 4, 5, 6]
# (offset, length) per part, straight from split_bootloader_a505.sh
EUB_PARTS_A505 = {
    1: (0x0, 0x2000),
    2: (0x2000, 0x15000 - 0x2000),
    3: (0x15000, 0x44000 - 0x15000),
    4: (0x5A000, 0x180000),
    5: (0x5A000 + 0x180000, 0x40000),
    6: (0x5A000 + 0x180000 + 0x40000, 0x31B000 - 0x5A000 - 0x180000 - 0x40000),
}


def eub_split_sboot(sboot_path):
    with open(sboot_path, "rb") as f:
        data = f.read()
    parts = {}
    for idx, (off, length) in EUB_PARTS_A505.items():
        chunk = data[off:off + length]
        if len(chunk) < length:
            raise ValueError(f"sboot.bin is too small for part{idx} "
                             f"(needs up to 0x{off + length:X}, file is 0x{len(data):X}).")
        parts[idx] = chunk
    return parts


def eub_packet(payload, load_addr):
    """[addr LE4][total_size LE4][payload][checksum LE2], total_size = len+10, cksum = sum(payload)&0xffff."""
    size = len(payload) + 10
    cksum = sum(payload) & 0xFFFF
    return (load_addr.to_bytes(4, "little") + size.to_bytes(4, "little")
            + payload + cksum.to_bytes(2, "little"))


def _libusb_backend():
    """Prefer a libusb-1.0.dll shipped next to the app, so pyusb works on a bare Windows box."""
    try:
        import usb.backend.libusb1 as libusb1
    except Exception:
        return None
    dll = next((os.path.join(d, "libusb-1.0.dll") for d in (exe_dir(), base_dir())
                if os.path.isfile(os.path.join(d, "libusb-1.0.dll"))), None)
    if dll:
        return libusb1.get_backend(find_library=lambda _n: dll)
    return libusb1.get_backend()


def eub_upload(parts, load_addr, on_line):
    try:
        import usb.core  # pyusb
    except ImportError:
        on_line("ERROR: pyusb is not installed. Run: pip install pyusb  (and install libusb).")
        return False
    try:
        dev = usb.core.find(idVendor=EUB_VID, idProduct=EUB_PID, backend=_libusb_backend())
    except Exception as e:
        on_line(f"ERROR: USB search failed: {e} (install libusb / the Zadig driver).")
        return False
    if dev is None:
        on_line(f"ERROR: No device in EUB mode ({EUB_VID:04x}:{EUB_PID:04x}). "
                "The phone must already be in EUB.")
        return False
    try:
        dev.set_configuration()
    except Exception as e:
        on_line(f"ERROR: set_configuration failed: {e}")
        return False
    on_line(f"=> device found in EUB mode ({EUB_VID:04x}:{EUB_PID:04x})")
    for i, idx in enumerate(EUB_SEQUENCE, 1):
        payload = parts[idx]
        pkt = eub_packet(payload, load_addr)
        on_line(f"[{i}/{len(EUB_SEQUENCE)}] part{idx}: {len(payload)} bytes -> 0x{load_addr:08X}")
        try:
            wrote = dev.write(EUB_OUT_EP, pkt, timeout=50000)
        except Exception as e:
            on_line(f"ERROR: USB write failed on part{idx}: {e}")
            return False
        if wrote != len(pkt):
            on_line(f"ERROR: short write on part{idx} ({wrote}/{len(pkt)} bytes).")
            return False
    on_line("=> all parts uploaded. The device should now boot the uploaded bootloader.")
    return True


# =================================================================
class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title(f"{APP_NAME} — {APP_TAGLINE}")
        self.geometry("960x940")
        self.minsize(860, 740)
        self.configure(bg=PANEL)
        try:
            ico = os.path.join(base_dir(), "zg.ico")
            if os.path.isfile(ico):
                self.iconbitmap(ico)
        except Exception:
            pass
        self.serial = None
        self.fb_serial = None
        self.device_map = {}
        self.flashing = False
        self.slot_vars = {}

        self._init_fonts()
        self._init_style()
        self._build_header()

        body = ttk.Frame(self, style="Body.TFrame", padding=(14, 10))
        body.pack(fill="both", expand=True)
        body.columnconfigure(0, weight=1)
        body.rowconfigure(1, weight=3)
        body.rowconfigure(2, weight=2, minsize=150)

        bar = ttk.Frame(body, style="Body.TFrame")
        bar.grid(row=0, column=0, sticky="ew", pady=(0, 10))
        ttk.Label(bar, text="Device", style="Section.TLabel").pack(side="left")
        ttk.Button(bar, text="Refresh / detect", style="Accent.TButton", command=self.detect).pack(side="right")
        self.device_box = ttk.Combobox(bar, state="readonly", width=46)
        self.device_box.pack(side="right", padx=8, fill="x", expand=True)
        self.device_box.bind("<<ComboboxSelected>>", self.on_select)

        nb = ttk.Notebook(body)
        nb.grid(row=1, column=0, sticky="nsew")
        device = self._scroll_tab(nb, "  Device  ")
        apps = self._scroll_tab(nb, "  Apps  ")
        fastboot = self._scroll_tab(nb, "  Fastboot  ")
        advanced = self._scroll_tab(nb, "  Advanced  ")
        samsung = self._scroll_tab(nb, "  Samsung flash  ")
        self._build_device(device)
        self._build_apps(apps)
        self._build_fastboot(fastboot)
        self._build_samsung(samsung)
        self._build_advanced(advanced)

        self._build_console(body)
        self.log("Ready. Connect a device and click Refresh / detect.")
        self.after(300, self.detect)

    # ---------- theme ----------
    def _init_fonts(self):
        fams = set(tkfont.families())
        ui = "Segoe UI" if "Segoe UI" in fams else ("DejaVu Sans" if "DejaVu Sans" in fams else "TkDefaultFont")
        mono = "Consolas" if "Consolas" in fams else ("DejaVu Sans Mono" if "DejaVu Sans Mono" in fams else "TkFixedFont")
        self.f_ui = tkfont.Font(family=ui, size=10)
        self.f_section = tkfont.Font(family=ui, size=11, weight="bold")
        self.f_wordmark = tkfont.Font(family=ui, size=17, weight="bold")
        self.f_tag = tkfont.Font(family=ui, size=9)
        self.f_logo = tkfont.Font(family=ui, size=18, weight="bold")
        self.f_mono = tkfont.Font(family=mono, size=10)

    def _init_style(self):
        st = ttk.Style(self)
        st.theme_use("clam")
        st.configure(".", background=PANEL, foreground=INK, font=self.f_ui)
        st.configure("Body.TFrame", background=PANEL)
        st.configure("Tab.TFrame", background=PANEL)
        st.configure("Header.TFrame", background=CHASSIS)
        st.configure("Section.TLabel", background=PANEL, foreground=INK, font=self.f_section)
        st.configure("Hint.TLabel", background=PANEL, foreground=MUTED)
        st.configure("Slot.TLabel", background=PANEL, foreground=INK)
        st.configure("TLabelframe", background=PANEL, bordercolor=LINE, relief="solid", borderwidth=1)
        st.configure("TLabelframe.Label", background=PANEL, foreground=INK, font=self.f_section)
        st.configure("TCheckbutton", background=PANEL, foreground=INK)
        st.map("TCheckbutton", background=[("active", PANEL)])
        st.configure("TEntry", fieldbackground=CARD, bordercolor=LINE, insertcolor=INK, padding=4)
        st.map("TEntry", bordercolor=[("focus", ACCENT)])

        # neutral button
        st.configure("TButton", background=CARD, foreground=INK, bordercolor=LINE,
                     relief="solid", borderwidth=1, padding=(12, 7), focusthickness=1, focuscolor=ACCENT)
        st.map("TButton", background=[("active", "#E3E6EB"), ("pressed", "#DADEE4")],
               bordercolor=[("active", "#C2C8D0")])
        # accent (primary)
        st.configure("Accent.TButton", background=ACCENT, foreground="#FFFFFF", bordercolor=ACCENT_DK)
        st.map("Accent.TButton", background=[("active", ACCENT_DK), ("pressed", ACCENT_DK)],
               foreground=[("disabled", "#F3D8C8")])
        # danger (destructive)
        st.configure("Danger.TButton", background=DANGER, foreground="#FFFFFF", bordercolor=DANGER_DK, padding=(16, 8))
        st.map("Danger.TButton", background=[("active", DANGER_DK), ("pressed", DANGER_DK)])

        st.configure("TCombobox", fieldbackground=CARD, background=CARD, bordercolor=LINE, arrowcolor=INK, padding=4)
        st.map("TCombobox", fieldbackground=[("readonly", CARD)], bordercolor=[("focus", ACCENT)])

        st.configure("TNotebook", background=PANEL, bordercolor=LINE, tabmargins=(2, 6, 2, 0))
        st.configure("TNotebook.Tab", background="#DFE3E8", foreground=MUTED,
                     padding=(14, 8), font=self.f_section, bordercolor=LINE)
        st.map("TNotebook.Tab", background=[("selected", PANEL)], foreground=[("selected", INK)],
               expand=[("selected", (0, 0, 0, 0))])

        st.configure("Treeview", background=CARD, fieldbackground=CARD, foreground=INK,
                     bordercolor=LINE, borderwidth=1, rowheight=24, font=self.f_ui)
        st.configure("Treeview.Heading", background="#E3E6EB", foreground=INK, font=self.f_section,
                     relief="flat", padding=6)
        st.map("Treeview", background=[("selected", "#FCE2D3")], foreground=[("selected", INK)])
        st.configure("Chip.TLabel", background=CARD, foreground=INK, padding=(8, 2))

    def _rounded(self, canvas, x1, y1, x2, y2, r, **kw):
        canvas.create_polygon(
            x1 + r, y1, x2 - r, y1, x2, y1, x2, y1 + r, x2, y2 - r, x2, y2, x2 - r, y2,
            x1 + r, y2, x1, y2, x1, y2 - r, x1, y1 + r, x1, y1, smooth=True, **kw)

    def _build_header(self):
        head = tk.Frame(self, bg=CHASSIS, height=74)
        head.pack(fill="x")
        head.pack_propagate(False)
        tk.Frame(self, bg=ACCENT, height=3).pack(fill="x")  # accent rule under the chassis

        # logo badge (original ZG chip monogram)
        logo = tk.Canvas(head, width=50, height=50, bg=CHASSIS, highlightthickness=0)
        logo.pack(side="left", padx=(16, 10), pady=12)
        self._rounded(logo, 3, 3, 47, 47, 11, fill=ACCENT, outline="")
        for i in range(3):  # chip pins
            y = 14 + i * 11
            logo.create_line(3, y, -2, y, fill=ACCENT_DK, width=3)
            logo.create_line(47, y, 52, y, fill=ACCENT_DK, width=3)
        logo.create_text(25, 26, text="ZG", fill="#FFFFFF", font=self.f_logo)

        words = tk.Frame(head, bg=CHASSIS)
        words.pack(side="left", pady=12)
        tk.Label(words, text=APP_NAME, bg=CHASSIS, fg=ON_DARK, font=self.f_wordmark).pack(anchor="w")
        tk.Label(words, text=APP_TAGLINE, bg=CHASSIS, fg="#9AA3AF", font=self.f_tag).pack(anchor="w")

        self.pill = tk.Canvas(head, width=200, height=34, bg=CHASSIS, highlightthickness=0)
        self.pill.pack(side="right", padx=16)
        self._set_status("muted", "No device")

    def _set_status(self, kind, text):
        colors = {"muted": "#39404A", "ok": OK, "warn": ACCENT}
        dot = {"muted": "#6B7480", "ok": "#BDEAD2", "warn": "#FFE0CC"}
        c = self.pill
        c.delete("all")
        self._rounded(c, 1, 1, 199, 33, 16, fill=colors.get(kind, "#39404A"), outline="")
        c.create_oval(14, 13, 24, 23, fill=dot.get(kind, "#6B7480"), outline="")
        c.create_text(36, 18, text=text, fill=ON_DARK, font=self.f_ui, anchor="w")

    # ---------- scrollable tab host ----------
    def _scroll_tab(self, nb, title):
        outer = ttk.Frame(nb, style="Tab.TFrame")
        nb.add(outer, text=title)
        canvas = tk.Canvas(outer, bg=PANEL, highlightthickness=0)
        vsb = ttk.Scrollbar(outer, orient="vertical", command=canvas.yview)
        inner = ttk.Frame(canvas, style="Tab.TFrame", padding=12)
        win = canvas.create_window((0, 0), window=inner, anchor="nw")
        inner.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.bind("<Configure>", lambda e: canvas.itemconfigure(win, width=e.width))
        canvas.configure(yscrollcommand=vsb.set)
        vsb.pack(side="right", fill="y")
        canvas.pack(side="left", fill="both", expand=True)

        def wheel(e):
            canvas.yview_scroll(-1 if getattr(e, "delta", 0) > 0 or e.num == 4 else 1, "units")
        for seq in ("<MouseWheel>", "<Button-4>", "<Button-5>"):
            inner.bind_all  # noop to keep linters quiet
        inner.bind("<Enter>", lambda e: [canvas.bind_all(s, wheel) for s in ("<MouseWheel>", "<Button-4>", "<Button-5>")])
        inner.bind("<Leave>", lambda e: [canvas.unbind_all(s) for s in ("<MouseWheel>", "<Button-4>", "<Button-5>")])
        return inner

    # ---------- small layout helpers ----------
    def _lf(self, parent, text):
        return ttk.Labelframe(parent, text=text, padding=10)

    def _row(self, parent):
        r = ttk.Frame(parent, style="Tab.TFrame")
        r.pack(fill="x", pady=2)
        return r

    def _btn(self, parent, text, cmd, style="TButton", **pack):
        b = ttk.Button(parent, text=text, style=style, command=cmd)
        b.pack(side="left", padx=(0, 6), **pack)
        return b

    # ================= DEVICE tab =================
    def _build_device(self, parent):
        info = self._lf(parent, "Device info")
        info.pack(fill="x", pady=(0, 8))
        r = self._row(info)
        self._btn(r, "Read device info", self.read_info, "Accent.TButton")
        self._btn(r, "Read phone info (MTP)", self.read_mtp)

        cap = self._lf(parent, "Capture")
        cap.pack(fill="x", pady=8)
        r = self._row(cap)
        self._btn(r, "Screenshot", self.screenshot)
        self._btn(r, "Screen record (30s)", self.screen_record)
        self._btn(r, "Mirror screen (scrcpy)", self.launch_scrcpy)

        reb = self._lf(parent, "Reboot")
        reb.pack(fill="x", pady=8)
        r = self._row(reb)
        self._btn(r, "Reboot", lambda: self.adb_cmd(["reboot"]))
        self._btn(r, "To recovery", lambda: self.adb_cmd(["reboot", "recovery"]))
        self._btn(r, "To bootloader", lambda: self.adb_cmd(["reboot", "bootloader"]))
        r2 = self._row(reb)
        self._btn(r2, "To fastbootd", lambda: self.adb_cmd(["reboot", "fastboot"]))
        self._btn(r2, "To download (Samsung)", lambda: self.adb_cmd(["reboot", "download"]))
        self._btn(r2, "To EDL (9008)", lambda: self.adb_cmd(["reboot", "edl"]))

    # ================= APPS tab =================
    def _build_apps(self, parent):
        box = self._lf(parent, "Installed apps")
        box.pack(fill="x", pady=(0, 8))
        top = self._row(box)
        ttk.Label(top, text="Show", style="Hint.TLabel").pack(side="left", padx=(0, 6))
        self.pkg_filter = ttk.Combobox(top, state="readonly", width=14,
                                       values=["User apps", "System apps", "All"])
        self.pkg_filter.set("User apps")
        self.pkg_filter.pack(side="left")
        self._btn(top, "List apps", self.refresh_packages, "Accent.TButton")
        ttk.Label(top, text="Filter", style="Hint.TLabel").pack(side="left", padx=(10, 6))
        self.pkg_search = ttk.Entry(top, width=22)
        self.pkg_search.pack(side="left")
        self.pkg_search.bind("<KeyRelease>", lambda e: self._apply_pkg_filter())

        table = ttk.Frame(box, style="Tab.TFrame")
        table.pack(fill="x", pady=(8, 0))
        self.pkg_tree = ttk.Treeview(table, columns=("pkg",), show="headings", height=9, selectmode="browse")
        self.pkg_tree.heading("pkg", text="Package")
        self.pkg_tree.column("pkg", width=700, anchor="w")
        pv = ttk.Scrollbar(table, orient="vertical", command=self.pkg_tree.yview)
        self.pkg_tree.configure(yscrollcommand=pv.set)
        pv.pack(side="right", fill="y")
        self.pkg_tree.pack(side="left", fill="x", expand=True)
        self._all_pkgs = []

        act = self._lf(parent, "App actions (selected app)")
        act.pack(fill="x", pady=8)
        r = self._row(act)
        self._btn(r, "Remove (this user)", self.pkg_uninstall_user, "Danger.TButton")
        self._btn(r, "Restore removed", self.pkg_restore)
        self._btn(r, "Full uninstall", self.pkg_uninstall_full)
        r2 = self._row(act)
        self._btn(r2, "Disable", self.pkg_disable)
        self._btn(r2, "Enable", self.pkg_enable)
        self._btn(r2, "Clear data", self.pkg_clear)
        self._btn(r2, "Extract APK", self.pkg_extract)
        ttk.Label(act, text="“Remove (this user)” debloats without root and is reversible with “Restore removed”.",
                  style="Hint.TLabel").pack(anchor="w", pady=(6, 0))

        inst = self._lf(parent, "Install")
        inst.pack(fill="x", pady=8)
        r = self._row(inst)
        self._btn(r, "Install APK", self.install_apk, "Accent.TButton")
        self._btn(r, "Install split APKs", self.install_multiple)

        files = self._lf(parent, "Files")
        files.pack(fill="x", pady=8)
        r = self._row(files)
        self._btn(r, "Push file → /sdcard/Download", self.push_file)
        self._btn(r, "List /sdcard", lambda: self.adb_shell_show(["ls", "-la", "/sdcard"]))
        r2 = self._row(files)
        ttk.Label(r2, text="Pull", style="Hint.TLabel").pack(side="left", padx=(0, 6))
        self.pull_var = ttk.Entry(r2, width=40)
        self.pull_var.insert(0, "/sdcard/")
        self.pull_var.pack(side="left", padx=(0, 6))
        self._btn(r2, "Pull to PC", self.pull_file)

    # ================= FASTBOOT tab =================
    def _build_fastboot(self, parent):
        ttk.Label(parent, text="These commands need the device in fastboot/bootloader mode (not adb).",
                  style="Hint.TLabel").pack(fill="x", pady=(0, 8))

        info = self._lf(parent, "Info")
        info.pack(fill="x", pady=4)
        r = self._row(info)
        self._btn(r, "getvar all", lambda: self._fb_async(["getvar", "all"]), "Accent.TButton")
        self._btn(r, "List devices", lambda: self.fb_cmd(["devices"]))

        bl = self._lf(parent, "Bootloader")
        bl.pack(fill="x", pady=8)
        r = self._row(bl)
        self._btn(r, "Unlock (erases data)", self.fb_unlock, "Danger.TButton")
        self._btn(r, "Lock", self.fb_lock)
        ttk.Label(bl, text="Unlocking wipes the device and is the official way to allow custom images.",
                  style="Hint.TLabel").pack(anchor="w", pady=(6, 0))

        fl = self._lf(parent, "Flash a partition")
        fl.pack(fill="x", pady=8)
        r = self._row(fl)
        ttk.Label(r, text="Partition", style="Hint.TLabel").pack(side="left", padx=(0, 6))
        self.fb_part_var = ttk.Combobox(r, width=16, values=FB_PARTITIONS)
        self.fb_part_var.set("boot")
        self.fb_part_var.pack(side="left", padx=(0, 10))
        self._btn(r, "Choose image & flash", self.fb_flash_partition, "Accent.TButton")
        r2 = self._row(fl)
        self._btn(r2, "Boot image once (no flash)", self.fb_boot_image)
        ttk.Label(r2, text="“Boot once” is safe for testing a recovery/boot image without writing it.",
                  style="Hint.TLabel").pack(side="left", padx=(4, 0))

        mt = self._lf(parent, "Maintenance")
        mt.pack(fill="x", pady=8)
        r = self._row(mt)
        ttk.Label(r, text="Erase", style="Hint.TLabel").pack(side="left", padx=(0, 6))
        self.fb_erase_var = ttk.Combobox(r, width=16, values=FB_PARTITIONS)
        self.fb_erase_var.set("cache")
        self.fb_erase_var.pack(side="left", padx=(0, 10))
        self._btn(r, "Erase partition", self.fb_erase, "Danger.TButton")
        r2 = self._row(mt)
        ttk.Label(r2, text="Active slot (A/B)", style="Hint.TLabel").pack(side="left", padx=(0, 6))
        self._btn(r2, "Set a", lambda: self.fb_set_active("a"))
        self._btn(r2, "Set b", lambda: self.fb_set_active("b"))

        rb = self._lf(parent, "Reboot")
        rb.pack(fill="x", pady=8)
        r = self._row(rb)
        self._btn(r, "Reboot", lambda: self.fb_cmd(["reboot"]))
        self._btn(r, "To bootloader", lambda: self.fb_cmd(["reboot", "bootloader"]))
        self._btn(r, "To fastbootd", lambda: self.fb_cmd(["reboot", "fastboot"]))
        self._btn(r, "To recovery", lambda: self.fb_cmd(["reboot", "recovery"]))

    # ================= SAMSUNG tab =================
    def _build_samsung(self, parent):
        warn = ("Flashes official firmware through Heimdall (open-source Odin). Flashing runs in download "
                "mode, not adb. Use firmware for the exact model, keep the battery charged, and don't unplug "
                "while flashing. On Windows: put heimdall.exe next to the app and install the download-mode "
                "driver once with Zadig.")
        ttk.Label(parent, text=warn, style="Hint.TLabel", wraplength=860, justify="left").pack(fill="x", pady=(0, 8))

        step1 = self._lf(parent, "1 · Enter download mode and detect")
        step1.pack(fill="x", pady=4)
        ttk.Button(step1, text="Reboot to download mode", style="Accent.TButton",
                   command=lambda: self.adb_cmd(["reboot", "download"])).pack(side="left")
        ttk.Button(step1, text="Detect device", command=self.heimdall_detect).pack(side="left", padx=6)
        ttk.Button(step1, text="Print PIT", command=self.heimdall_pit).pack(side="left")
        ttk.Button(step1, text="Driver help", command=self.driver_help).pack(side="left", padx=6)

        step2 = self._lf(parent, "2 · Firmware files (one tar.md5 per slot, like Odin)")
        step2.pack(fill="x", pady=4)
        slots = [("BL", "Bootloader"), ("AP", "System (large file)"), ("CP", "Modem / network"),
                 ("CSC", "Wipes device data"), ("HOME_CSC", "Keeps your data")]
        grid = ttk.Frame(step2, style="Tab.TFrame")
        grid.pack(fill="x")
        for row, (slot, hint) in enumerate(slots):
            ttk.Label(grid, text=slot, style="Slot.TLabel", width=10).grid(row=row, column=0, sticky="w", pady=3)
            ttk.Label(grid, text=hint, style="Hint.TLabel", width=18).grid(row=row, column=1, sticky="w", padx=(0, 8))
            var = tk.StringVar(value="—")
            self.slot_vars[slot] = var
            ttk.Label(grid, textvariable=var, style="Chip.TLabel", width=44, anchor="w",
                      relief="solid", borderwidth=1).grid(row=row, column=2, sticky="ew", padx=4, pady=3)
            ttk.Button(grid, text="Choose", width=9,
                       command=lambda s=slot: self.pick_slot(s)).grid(row=row, column=3, padx=4)
        grid.columnconfigure(2, weight=1)
        extra = ttk.Frame(step2, style="Tab.TFrame")
        extra.pack(fill="x", pady=(8, 0))
        ttk.Button(extra, text="Add single image", command=self.add_single_image).pack(side="left")
        ttk.Button(extra, text="Remove selected", command=self.remove_selected).pack(side="left", padx=6)
        ttk.Button(extra, text="Clear all", command=self.clear_mapping).pack(side="left")

        eub = self._lf(parent, "Emergency recovery — upload bootloader (Exynos 9610 / A50)")
        eub.pack(fill="x", pady=4)
        ttk.Label(eub, wraplength=860, justify="left", style="Hint.TLabel",
                  text=("Unbricks a device that is ALREADY in EUB mode by uploading a bootloader over USB "
                        "(open-source smdk-usbdl protocol). It does NOT enter EUB and does NOT bypass any lock. "
                        "Only for Exynos 9610 / A505 — a wrong sboot.bin can brick further. "
                        "On Windows install the libusb driver for the 04e8:1234 device with Zadig.")
                  ).pack(fill="x", pady=(0, 6))
        er = ttk.Frame(eub, style="Tab.TFrame")
        er.pack(fill="x")
        ttk.Label(er, text="Load address", style="Hint.TLabel").pack(side="left", padx=(0, 6))
        self.eub_addr = ttk.Entry(er, width=14)
        self.eub_addr.insert(0, "0x30000000")
        self.eub_addr.pack(side="left", padx=(0, 10))
        ttk.Button(er, text="Choose sboot.bin & recover", style="Danger.TButton",
                   command=self.eub_recover).pack(side="left")

        table = ttk.Frame(parent, style="Tab.TFrame")
        table.pack(fill="x", pady=6)
        self.tree = ttk.Treeview(table, columns=("partition", "file"), show="headings",
                                 selectmode="extended", height=6)
        self.tree.heading("partition", text="Partition")
        self.tree.heading("file", text="Image file")
        self.tree.column("partition", width=150, anchor="w")
        self.tree.column("file", width=560, anchor="w")
        vsb = ttk.Scrollbar(table, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=vsb.set)
        vsb.pack(side="right", fill="y")
        self.tree.pack(side="left", fill="both", expand=True)
        self.tree.bind("<Double-1>", self.edit_partition)

        step3 = self._lf(parent, "3 · Flash")
        step3.pack(fill="x", pady=(6, 0))
        ttk.Button(step3, text="Flash now", style="Danger.TButton", command=self.flash).pack(side="left")
        self.no_reboot_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(step3, text="Don't reboot after flashing", variable=self.no_reboot_var).pack(side="left", padx=10)
        ttk.Label(step3, text="Double-click a row to edit its partition name.", style="Hint.TLabel").pack(side="left")

    # ================= ADVANCED tab =================
    def _build_advanced(self, parent):
        sh = self._lf(parent, "ADB shell")
        sh.pack(fill="x", pady=(0, 8))
        r = self._row(sh)
        self.shell_var = ttk.Entry(r)
        self.shell_var.insert(0, "getprop ro.product.model")
        self.shell_var.pack(side="left", fill="x", expand=True, padx=(0, 6))
        self.shell_var.bind("<Return>", lambda e: self.run_shell())
        self._btn(r, "Run", self.run_shell, "Accent.TButton")

        diag = self._lf(parent, "Diagnostics")
        diag.pack(fill="x", pady=8)
        r = self._row(diag)
        self._btn(r, "Logcat (last 500)", self.logcat)
        self._btn(r, "Kernel log (dmesg)", lambda: self.adb_shell_show(["dmesg"], 120))
        self._btn(r, "Save bug report", self.bugreport)
        r2 = self._row(diag)
        self._btn(r2, "Battery", lambda: self.adb_shell_show(["dumpsys", "battery"]))
        self._btn(r2, "Storage (df)", lambda: self.adb_shell_show(["df", "-h"]))
        self._btn(r2, "Running (top -n1)", lambda: self.adb_shell_show(["top", "-n", "1", "-b"], 60))

        wl = self._lf(parent, "Wireless ADB")
        wl.pack(fill="x", pady=8)
        r = self._row(wl)
        self._btn(r, "Enable over Wi-Fi (tcpip 5555)", self.adb_wireless, "Accent.TButton")
        r2 = self._row(wl)
        ttk.Label(r2, text="Connect to", style="Hint.TLabel").pack(side="left", padx=(0, 6))
        self.ip_var = ttk.Entry(r2, width=22)
        self.ip_var.insert(0, "192.168.1.100:5555")
        self.ip_var.pack(side="left", padx=(0, 6))
        self._btn(r2, "Connect", self.adb_connect)
        self._btn(r2, "Disconnect all", lambda: self._adb_raw(["disconnect"]))

        up = self._lf(parent, "Update / recovery")
        up.pack(fill="x", pady=8)
        r = self._row(up)
        self._btn(r, "Sideload OTA (recovery)", self.adb_sideload)
        ttk.Label(up, text="Put the phone in recovery → Apply update from ADB, then pick the zip.",
                  style="Hint.TLabel").pack(anchor="w", pady=(6, 0))

    def _build_console(self, parent):
        wrap = tk.Frame(parent, bg=CONSOLE_BG, highlightthickness=1, highlightbackground=LINE)
        wrap.grid(row=2, column=0, sticky="nsew", pady=(10, 0))
        topbar = tk.Frame(wrap, bg=CONSOLE_BG)
        topbar.pack(fill="x")
        tk.Frame(wrap, bg=ACCENT, height=2).pack(fill="x")
        tk.Label(topbar, text="Console", bg=CONSOLE_BG, fg=CONSOLE_DIM, font=self.f_section).pack(side="left", padx=10, pady=4)
        tk.Button(topbar, text="Clear", command=lambda: self.out.delete("1.0", "end"), bg=CONSOLE_BG, fg=CONSOLE_FG,
                  activebackground="#1B222B", activeforeground=ON_DARK, relief="flat", font=self.f_ui,
                  cursor="hand2").pack(side="right", padx=4)
        tk.Button(topbar, text="Save log", command=self.save_report, bg=CONSOLE_BG, fg=CONSOLE_FG,
                  activebackground="#1B222B", activeforeground=ON_DARK, relief="flat", font=self.f_ui,
                  cursor="hand2").pack(side="right")
        self.out = scrolledtext.ScrolledText(wrap, font=self.f_mono, wrap="word", height=7, relief="flat",
                                             bg=CONSOLE_BG, fg=CONSOLE_FG, insertbackground=ACCENT,
                                             selectbackground="#2A3440", padx=10, pady=8, borderwidth=0)
        self.out.pack(fill="both", expand=True)
        self.out.tag_configure("cmd", foreground=ACCENT)
        self.out.tag_configure("err", foreground="#FF8A7A")

    # ---------- console helpers ----------
    def log(self, text, tag=None):
        tags = ()
        if tag:
            tags = (tag,)
        elif text.startswith(">"):
            tags = ("cmd",)
        elif "ERROR" in text or text.lower().startswith(("failed", "could not")):
            tags = ("err",)
        self.out.insert("end", text + "\n", tags)
        self.out.see("end")

    def bg(self, fn):
        threading.Thread(target=fn, daemon=True).start()

    def post(self, text, tag=None):
        self.after(0, lambda: self.log(text, tag))

    def adb(self, *args):
        return [ADB] + (["-s", self.serial] if self.serial else []) + list(args)

    def fastboot(self, *args):
        return [FASTBOOT] + (["-s", self.fb_serial] if self.fb_serial else []) + list(args)

    def target_name(self):
        return self.device_box.get() or "the device"

    # ---------- devices ----------
    def detect(self):
        def job():
            adb_out = run([ADB, "devices", "-l"])
            fb_out = run([FASTBOOT, "devices"])
            adb_list = parse_adb_devices(adb_out) if not adb_out.startswith("ERROR") else []
            fb_list = parse_fastboot_devices(fb_out) if not fb_out.startswith("ERROR") else []
            self.after(0, lambda: self.fill_devices(adb_list, fb_list, adb_out))
        self.bg(job)

    def fill_devices(self, adb_list, fb_list, adb_out):
        self.device_map = {}
        for serial, state, model in adb_list:
            self.device_map[f"{model or 'device'}  ({serial})  [adb: {state}]"] = ("adb", serial)
        for serial in fb_list:
            self.device_map[f"{serial}  [fastboot]"] = ("fastboot", serial)
        labels = list(self.device_map)
        self.device_box["values"] = labels
        self.log("> detect devices")
        if adb_out.startswith("ERROR"):
            self.log(adb_out)
        if not labels:
            self.device_box.set("")
            self.serial = self.fb_serial = None
            self._set_status("muted", "No device")
            self.log("No adb/fastboot device. (Download mode is detected from the Samsung tab.)")
            return
        for label in labels:
            self.log("  " + label)
        current = next((l for l, v in self.device_map.items() if v[1] in (self.serial, self.fb_serial)), labels[0])
        self.device_box.set(current)
        self.on_select()
        if len(labels) > 1:
            self.log("More than one device: pick the one to work on from the list.")

    def on_select(self, _event=None):
        kind, serial = self.device_map.get(self.device_box.get(), (None, None))
        self.serial = serial if kind == "adb" else None
        self.fb_serial = serial if kind == "fastboot" else None
        if kind == "adb":
            model = self.device_box.get().split("  (")[0]
            self._set_status("ok", model[:22])
        elif kind == "fastboot":
            self._set_status("warn", "Fastboot")
        else:
            self._set_status("muted", "No device")

    def ensure_adb(self):
        state = run(self.adb("get-state"))
        if adb_ready(state):
            return True
        self.post(f"Device not ready for adb: {explain_state(state)}", "err")
        return False

    # ---------- generic async runners ----------
    def _run_adb_async(self, args, timeout=120, done="Done."):
        def job():
            if not self.ensure_adb():
                return
            self.post(f"> adb {' '.join(args)}")
            self.post(run(self.adb(*args), timeout=timeout) or done)
        self.bg(job)

    def adb_shell_show(self, args, timeout=60):
        def job():
            if not self.ensure_adb():
                return
            self.post(f"> adb shell {' '.join(args)}")
            self.post(run(self.adb("shell", *args), timeout=timeout) or "(no output)")
        self.bg(job)

    def _adb_raw(self, args):
        def job():
            self.post(f"> adb {' '.join(args)}")
            self.post(run([ADB] + args) or "Done.")
            self.after(300, self.detect)
        self.bg(job)

    def _fb_async(self, args, timeout=120):
        def job():
            self.post(f"> fastboot {' '.join(args)}")
            self.post(run(self.fastboot(*args), timeout=timeout) or "Done.")
        self.bg(job)

    # ---------- device info ----------
    def read_info(self):
        def job():
            if not self.ensure_adb():
                return
            fields = [("Brand", "ro.product.brand"), ("Model", "ro.product.model"),
                      ("Codename", "ro.product.device"), ("Android", "ro.build.version.release"),
                      ("SDK", "ro.build.version.sdk"), ("Build", "ro.build.display.id"),
                      ("Security patch", "ro.build.version.security_patch"), ("Serial", "ro.serialno"),
                      ("CPU (ABI)", "ro.product.cpu.abi"), ("Bootloader", "ro.bootloader"),
                      ("Lock state", "ro.boot.flash.locked"), ("Verified boot", "ro.boot.verifiedbootstate")]
            self.post(f"> device info: {self.target_name()}")
            for label, key in fields:
                val = run(self.adb("shell", "getprop", key))
                if not val or val.startswith(("ERROR", "error:")):
                    val = "-"
                self.post(f"  {label}: {val}")
            batt = run(self.adb("shell", "dumpsys", "battery"))
            for line in batt.splitlines():
                line = line.strip()
                if line.startswith(("level", "temperature", "status", "health")):
                    self.post(f"  battery {line}")
        self.bg(job)

    def read_mtp(self):
        def job():
            self.post("> phone info over MTP")
            self.post(read_mtp_devices())
        self.bg(job)

    # ---------- capture ----------
    def screenshot(self):
        path = filedialog.asksaveasfilename(title="Save screenshot", defaultextension=".png",
                                            initialfile=time.strftime("screenshot_%Y%m%d_%H%M%S.png"),
                                            filetypes=[("PNG", "*.png")])
        if not path:
            return

        def job():
            if not self.ensure_adb():
                return
            data, err = run_bytes(self.adb("exec-out", "screencap", "-p"))
            if err or not data or not data.startswith(b"\x89PNG"):
                self.post(f"Screenshot failed: {err or 'no image (unlock the screen and try again).'}", "err")
                return
            with open(path, "wb") as f:
                f.write(data)
            self.post(f"Saved screenshot: {path}")
        self.bg(job)

    def screen_record(self):
        path = filedialog.asksaveasfilename(title="Save recording", defaultextension=".mp4",
                                            initialfile=time.strftime("screenrec_%Y%m%d_%H%M%S.mp4"),
                                            filetypes=[("MP4", "*.mp4")])
        if not path:
            return

        def job():
            if not self.ensure_adb():
                return
            remote = "/sdcard/zg_screenrec.mp4"
            self.post("> screen record (up to 30s — use the phone now)")
            run(self.adb("shell", "screenrecord", "--time-limit", "30", remote), timeout=60)
            out = run(self.adb("pull", remote, path), timeout=120)
            run(self.adb("shell", "rm", remote))
            self.post(out if out.startswith(("ERROR", "error:")) else f"Saved recording: {path}")
        self.bg(job)

    def launch_scrcpy(self):
        def job():
            self.post("> scrcpy (screen mirror)")
            try:
                subprocess.Popen([SCRCPY] + (["-s", self.serial] if self.serial else []),
                                 creationflags=NO_WINDOW)
                self.post("scrcpy launched in its own window.")
            except FileNotFoundError:
                self.post("scrcpy not found. Download scrcpy (open-source) and put scrcpy.exe next to the app.", "err")
        self.bg(job)

    # ---------- apps ----------
    def refresh_packages(self):
        kind = self.pkg_filter.get()
        flag = {"User apps": "-3", "System apps": "-s", "All": ""}.get(kind, "")

        def job():
            if not self.ensure_adb():
                return
            self.post(f"> list packages ({kind})")
            args = ["shell", "pm", "list", "packages"]
            if flag:
                args.append(flag)
            out = run(self.adb(*args), timeout=60)
            if out.startswith(("ERROR", "error:")):
                self.post(out, "err")
                return
            pkgs = sorted(l.split(":", 1)[1].strip() for l in out.splitlines() if l.startswith("package:"))
            self._all_pkgs = pkgs
            self.after(0, self._apply_pkg_filter)
            self.post(f"  {len(pkgs)} packages")
        self.bg(job)

    def _apply_pkg_filter(self):
        q = self.pkg_search.get().strip().lower()
        self.pkg_tree.delete(*self.pkg_tree.get_children())
        for p in self._all_pkgs:
            if not q or q in p.lower():
                self.pkg_tree.insert("", "end", values=(p,))

    def _sel_pkg(self):
        sel = self.pkg_tree.selection()
        if not sel:
            messagebox.showinfo("Apps", "Select an app from the list first.")
            return None
        return self.pkg_tree.item(sel[0], "values")[0]

    def pkg_uninstall_user(self):
        pkg = self._sel_pkg()
        if not pkg:
            return
        if not messagebox.askyesno("Remove app",
                                   f"Remove '{pkg}' for the current user?\n\n"
                                   "This debloats without root and is reversible with 'Restore removed'."):
            return
        self._run_adb_async(["shell", "pm", "uninstall", "--user", "0", pkg])

    def pkg_restore(self):
        pkg = self._sel_pkg()
        if pkg:
            self._run_adb_async(["shell", "cmd", "package", "install-existing", pkg])

    def pkg_uninstall_full(self):
        pkg = self._sel_pkg()
        if not pkg:
            return
        if messagebox.askyesno("Uninstall", f"Fully uninstall '{pkg}'?"):
            self._run_adb_async(["uninstall", pkg])

    def pkg_disable(self):
        pkg = self._sel_pkg()
        if pkg:
            self._run_adb_async(["shell", "pm", "disable-user", "--user", "0", pkg])

    def pkg_enable(self):
        pkg = self._sel_pkg()
        if pkg:
            self._run_adb_async(["shell", "pm", "enable", pkg])

    def pkg_clear(self):
        pkg = self._sel_pkg()
        if not pkg:
            return
        if messagebox.askyesno("Clear data", f"Erase all data for '{pkg}'?\nThis cannot be undone."):
            self._run_adb_async(["shell", "pm", "clear", pkg])

    def pkg_extract(self):
        pkg = self._sel_pkg()
        if not pkg:
            return
        save = filedialog.asksaveasfilename(title="Save APK", defaultextension=".apk",
                                            initialfile=f"{pkg}.apk", filetypes=[("APK", "*.apk")])
        if not save:
            return

        def job():
            if not self.ensure_adb():
                return
            self.post(f"> extract APK: {pkg}")
            out = run(self.adb("shell", "pm", "path", pkg))
            remote = next((l.split(":", 1)[1].strip() for l in out.splitlines() if l.startswith("package:")), "")
            if not remote:
                self.post("Could not find the APK path (app may be a split).", "err")
                return
            res = run(self.adb("pull", remote, save), timeout=300)
            self.post(res if res.startswith(("ERROR", "error:")) else f"Saved APK: {save}")
        self.bg(job)

    def install_apk(self):
        path = filedialog.askopenfilename(title="Choose an APK", filetypes=[("APK", "*.apk"), ("All files", "*.*")])
        if not path:
            return

        def job():
            if not self.ensure_adb():
                return
            self.post(f"> adb install -r {os.path.basename(path)} (may take a minute)")
            self.post(run(self.adb("install", "-r", path), timeout=300) or "Done.")
        self.bg(job)

    def install_multiple(self):
        paths = filedialog.askopenfilenames(title="Choose split APKs (base + config APKs)",
                                            filetypes=[("APK", "*.apk"), ("All files", "*.*")])
        if not paths:
            return

        def job():
            if not self.ensure_adb():
                return
            self.post(f"> adb install-multiple ({len(paths)} files)")
            self.post(run(self.adb("install-multiple", "-r", *paths), timeout=600) or "Done.")
        self.bg(job)

    def push_file(self):
        local = filedialog.askopenfilename(title="Choose a file to push to the phone")
        if not local:
            return
        remote = "/sdcard/Download/" + os.path.basename(local)

        def job():
            if not self.ensure_adb():
                return
            self.post(f"> adb push -> {remote}")
            self.post(run(self.adb("push", local, remote), timeout=600) or "Done.")
        self.bg(job)

    def pull_file(self):
        remote = self.pull_var.get().strip()
        if not remote:
            return
        save = filedialog.asksaveasfilename(title="Save to PC", initialfile=os.path.basename(remote.rstrip("/")) or "pulled")
        if not save:
            return

        def job():
            if not self.ensure_adb():
                return
            self.post(f"> adb pull {remote}")
            res = run(self.adb("pull", remote, save), timeout=600)
            self.post(res if res.startswith(("ERROR", "error:")) else f"Saved: {save}")
        self.bg(job)

    # ---------- fastboot ----------
    def fb_unlock(self):
        if not messagebox.askyesno("Unlock bootloader",
                                   "This ERASES ALL DATA on the device and may void the warranty.\n"
                                   "You may also need to allow OEM unlocking in Developer options first.\n\n"
                                   "Continue?"):
            return
        self._fb_async(["flashing", "unlock"])

    def fb_lock(self):
        if not messagebox.askyesno("Lock bootloader",
                                   "Re-lock the bootloader? Flash only official images first, "
                                   "or the device may refuse to boot.\n\nContinue?"):
            return
        self._fb_async(["flashing", "lock"])

    def fb_flash_partition(self):
        part = self.fb_part_var.get().strip()
        if not part:
            messagebox.showinfo("Flash", "Pick a partition first.")
            return
        img = filedialog.askopenfilename(title=f"Image for {part}",
                                         filetypes=[("Images", "*.img *.bin"), ("All files", "*.*")])
        if not img:
            return
        if not messagebox.askyesno("Flash partition",
                                   f"fastboot flash {part}\n{os.path.basename(img)}\n\n"
                                   "Use an image for this exact model. Continue?"):
            return
        cmd = self.fastboot("flash", part, img)

        def job():
            self.post(f"> fastboot flash {part} {os.path.basename(img)}")
            code = run_stream(cmd, self.post, timeout=600)
            self.post("Done." if code == 0 else f"Failed (code {code}).", None if code == 0 else "err")
        self.bg(job)

    def fb_boot_image(self):
        img = filedialog.askopenfilename(title="Image to boot once (not flashed)",
                                         filetypes=[("Images", "*.img *.bin"), ("All files", "*.*")])
        if not img:
            return
        cmd = self.fastboot("boot", img)

        def job():
            self.post(f"> fastboot boot {os.path.basename(img)}")
            code = run_stream(cmd, self.post, timeout=120)
            self.post("Done." if code == 0 else f"Failed (code {code}).", None if code == 0 else "err")
        self.bg(job)

    def fb_erase(self):
        part = self.fb_erase_var.get().strip()
        if not part:
            return
        if not messagebox.askyesno("Erase partition",
                                   f"fastboot erase {part}\n\nThis wipes that partition. Continue?"):
            return
        self._fb_async(["erase", part])

    def fb_set_active(self, slot):
        self._fb_async([f"--set-active={slot}"])

    def fb_cmd(self, args):
        if args != ["devices"]:
            if not messagebox.askyesno("Confirm", f"Run: fastboot {' '.join(args)}\nOn: {self.target_name()}\nContinue?"):
                return

        def job():
            self.post(f"> fastboot {' '.join(args)}")
            cmd = [FASTBOOT, "devices"] if args == ["devices"] else self.fastboot(*args)
            self.post(run(cmd) or ("(no device in fastboot mode)" if args == ["devices"] else "Done."))
        self.bg(job)

    # ---------- advanced ----------
    def run_shell(self):
        cmd = self.shell_var.get().strip()
        if not cmd:
            return

        def job():
            if not self.ensure_adb():
                return
            self.post(f"> adb shell {cmd}")
            self.post(run(self.adb("shell", cmd), timeout=120) or "(no output)")
        self.bg(job)

    def logcat(self):
        def job():
            if not self.ensure_adb():
                return
            self.post("> logcat (last 500 lines)")
            self.post(run(self.adb("logcat", "-d", "-t", "500"), timeout=60))
        self.bg(job)

    def bugreport(self):
        save = filedialog.asksaveasfilename(title="Save bug report", defaultextension=".zip",
                                            initialfile=time.strftime("bugreport_%Y%m%d_%H%M%S.zip"),
                                            filetypes=[("Zip", "*.zip")])
        if not save:
            return

        def job():
            if not self.ensure_adb():
                return
            self.post("> adb bugreport (can take a few minutes)")
            res = run(self.adb("bugreport", save), timeout=600)
            self.post(res if res.startswith(("ERROR", "error:")) else f"Saved bug report: {save}")
        self.bg(job)

    def adb_wireless(self):
        def job():
            if not self.ensure_adb():
                return
            self.post("> adb tcpip 5555")
            self.post(run(self.adb("tcpip", "5555")) or
                      "Done. Unplug USB, then use Connect with the phone's IP (Settings > About > Status).")
        self.bg(job)

    def adb_connect(self):
        ip = self.ip_var.get().strip()
        if not ip:
            return
        if ":" not in ip:
            ip += ":5555"
        self._adb_raw(["connect", ip])

    def adb_sideload(self):
        path = filedialog.askopenfilename(title="Choose OTA/update zip",
                                          filetypes=[("Zip", "*.zip"), ("All files", "*.*")])
        if not path:
            return
        if not messagebox.askyesno("Sideload",
                                   "The phone must be in recovery > 'Apply update from ADB'.\n\nContinue?"):
            return
        cmd = self.adb("sideload", path)

        def job():
            self.post(f"> adb sideload {os.path.basename(path)}")
            code = run_stream(cmd, self.post, timeout=3600)
            self.post("=== sideload done ===" if code == 0
                      else f"=== sideload failed (code {code}) ===", None if code == 0 else "err")
        self.bg(job)

    # ---------- Samsung (Heimdall) ----------
    def heimdall_detect(self):
        def job():
            self.post("> heimdall detect")
            out = run([HEIMDALL, "detect"])
            self.post(out or "No reply.")
            if "detected" not in out.lower() and not out.startswith("ERROR"):
                self.post("Tip: make sure the phone is in download mode and the driver is installed (Driver help).")
        self.bg(job)

    def heimdall_pit(self):
        def job():
            self.post("> heimdall print-pit")
            self.post(run([HEIMDALL, "print-pit"], timeout=60) or "No reply. The device must be in download mode.")
        self.bg(job)

    def driver_help(self):
        messagebox.showinfo(
            "Install the download-mode driver (Windows)",
            "1) Put the phone in download mode (Reboot to download mode).\n"
            "2) Open Zadig.\n"
            "3) Options > List All Devices, pick the Samsung device (MSM8x60 / Gadget Serial / SAMSUNG).\n"
            "4) Choose the libusb-win32 driver and click Replace/Install Driver.\n"
            "5) Back here, click Detect device.\n\n"
            "Note: install this driver on the device in download mode only, not in normal mode.")

    def add_to_mapping(self, partition, path, slot=""):
        self.tree.insert("", "end", values=(partition, path), tags=(slot,) if slot else ())

    def remove_slot_rows(self, slot):
        for item in self.tree.get_children():
            if slot in self.tree.item(item, "tags"):
                self.tree.delete(item)

    def pick_slot(self, slot):
        title = f"Choose the {slot} file"
        if slot == "CSC":
            title += " (wipes data — use HOME_CSC to keep data)"
        path = filedialog.askopenfilename(title=title,
                                          filetypes=[("Samsung firmware", "*.tar.md5 *.tar"), ("All files", "*.*")])
        if not path:
            return
        if slot in ("CSC", "HOME_CSC"):
            other = "HOME_CSC" if slot == "CSC" else "CSC"
            self.remove_slot_rows(other)
            self.slot_vars[other].set("—")
        self.remove_slot_rows(slot)
        self.slot_vars[slot].set(os.path.basename(path))

        def job():
            self.post(f"> [{slot}] extract {os.path.basename(path)}")
            try:
                dest = os.path.join(os.path.dirname(path), "extracted_" + slot)
                images = extract_tar_images(path, dest)
            except Exception as e:
                self.post(f"Failed to extract {os.path.basename(path)}: {e}", "err")
                self.after(0, lambda: self.slot_vars[slot].set("extract failed"))
                return
            if not images:
                self.post("No partition images inside (may be lz4-compressed — Heimdall still handles it).")
            for img in images:
                part = guess_partition(img)
                self.after(0, lambda p=part, i=img, s=slot: self.add_to_mapping(p, i, s))
                self.post(f"  [{slot}] {os.path.basename(img)}  ->  {part}")
            self.post("Check the partition names (double-click to edit) before flashing.")
        self.bg(job)

    def eub_recover(self):
        if not messagebox.askyesno(
                "Emergency recovery (EUB)",
                "Upload a bootloader to a device ALREADY in EUB mode (04e8:1234) to unbrick it.\n\n"
                "• Exynos 9610 / Galaxy A50 (A505) only — a wrong sboot.bin can brick further.\n"
                "• This does NOT put the device into EUB and does NOT bypass any lock.\n"
                "• Windows: install the libusb driver for 04e8:1234 with Zadig first.\n\n"
                "Continue?"):
            return
        sboot = filedialog.askopenfilename(title="Choose sboot.bin (it will be split into parts)",
                                           filetypes=[("Bootloader", "*.bin *.img"), ("All files", "*.*")])
        if not sboot:
            return
        try:
            addr = int(self.eub_addr.get().strip(), 16)
        except ValueError:
            messagebox.showerror("EUB", "Load address must be hex, e.g. 0x30000000.")
            return

        def job():
            self.post("=== EUB emergency recovery ===")
            try:
                parts = eub_split_sboot(sboot)
            except Exception as e:
                self.post(f"ERROR: split failed: {e}", "err")
                return
            ok = eub_upload(parts, addr, self.post)
            self.post("=== recovery done ===" if ok else "=== recovery failed ===",
                      None if ok else "err")
        self.bg(job)

    def add_single_image(self):
        path = filedialog.askopenfilename(title="Choose a partition image",
                                          filetypes=[("Images", "*.img *.bin *.lz4 *.mbn *.pit"), ("All files", "*.*")])
        if path:
            self.add_to_mapping(guess_partition(path), path)

    def remove_selected(self):
        for item in self.tree.selection():
            self.tree.delete(item)

    def clear_mapping(self):
        for item in self.tree.get_children():
            self.tree.delete(item)
        for var in self.slot_vars.values():
            var.set("—")

    def edit_partition(self, _event):
        item = self.tree.focus()
        if not item:
            return
        part, path = self.tree.item(item, "values")
        dlg = tk.Toplevel(self)
        dlg.title("Partition name")
        dlg.configure(bg=PANEL)
        dlg.transient(self)
        dlg.grab_set()
        ttk.Label(dlg, text=f"File: {os.path.basename(path)}", style="Hint.TLabel").pack(padx=16, pady=(14, 6))
        var = tk.StringVar(value=part)
        ttk.Entry(dlg, textvariable=var, justify="center", width=30).pack(padx=16)
        ttk.Label(dlg, text="Must match the partition name in the PIT exactly.",
                  style="Hint.TLabel").pack(padx=16, pady=(6, 0))

        def ok():
            name = var.get().strip()
            if name:
                self.tree.item(item, values=(name, path))
            dlg.destroy()
        ttk.Button(dlg, text="Save", style="Accent.TButton", command=ok).pack(pady=12)

    def flash(self):
        if self.flashing:
            self.post("A flash is already running.")
            return
        rows = [self.tree.item(i, "values") for i in self.tree.get_children()]
        mapping = [(p.strip(), f) for p, f in rows if p.strip()]
        if not mapping:
            messagebox.showwarning("Flash", "Add firmware files first.")
            return
        missing = [f for _, f in mapping if not os.path.isfile(f)]
        if missing:
            messagebox.showerror("Flash", "Missing files:\n" + "\n".join(missing))
            return
        summary = "\n".join(f"{p}  <-  {os.path.basename(f)}" for p, f in mapping)
        if not messagebox.askyesno("Confirm flash",
                                   "These partitions will be written:\n\n" + summary +
                                   "\n\nUse firmware for this exact model and keep the device in download mode.\n"
                                   "Do not unplug. Continue?"):
            return
        cmd = build_flash_cmd(mapping, no_reboot=self.no_reboot_var.get())
        self.flashing = True

        def job():
            self.post("=== flashing started ===")
            self.post("> " + " ".join(os.path.basename(c) if os.path.sep in c else c for c in cmd))
            code = run_stream(cmd, self.post, timeout=3600)
            self.post("=== flashing done (code 0) ===" if code == 0
                      else f"=== flashing failed (code {code}). Check the messages above. ===", None if code == 0 else "err")
            self.flashing = False
        self.bg(job)

    # ---------- save ----------
    def save_report(self):
        text = self.out.get("1.0", "end").strip()
        if not text:
            messagebox.showinfo("Save log", "Nothing to save.")
            return
        path = filedialog.asksaveasfilename(title="Save log", defaultextension=".txt",
                                            initialfile=time.strftime("zg_log_%Y%m%d_%H%M%S.txt"),
                                            filetypes=[("Text", "*.txt")])
        if not path:
            return
        with open(path, "w", encoding="utf-8") as f:
            f.write(text + "\n")
        self.log(f"Saved log: {path}")

    # ---------- commands ----------
    def adb_cmd(self, args):
        if args[-1] in ("bootloader", "download", "recovery", "fastboot", "edl"):
            if not messagebox.askyesno("Confirm", f"Run: adb {' '.join(args)}\nOn: {self.target_name()}\nContinue?"):
                return

        def job():
            if not self.ensure_adb():
                return
            self.post(f"> adb {' '.join(args)}")
            self.post(run(self.adb(*args)) or "Done.")
        self.bg(job)


if __name__ == "__main__":
    App().mainloop()
