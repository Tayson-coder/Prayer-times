# -*- mode: python ; coding: utf-8 -*-
# PyInstaller spec for ZG — one-file Windows build.
# Build:  pyinstaller zg.spec

import os

datas = []
if os.path.isfile("zg.ico"):
    datas.append(("zg.ico", "."))

a = Analysis(
    ["zg_tool.py"],
    pathex=[],
    binaries=[],
    datas=datas,
    hiddenimports=["usb", "usb.core", "usb.util", "usb.backend.libusb1"],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="ZG",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    runtime_tmpdir=None,
    console=False,              # GUI app, no console window
    icon="zg.ico" if os.path.isfile("zg.ico") else None,
)
