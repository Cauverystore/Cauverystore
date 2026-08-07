# -*- mode: python ; coding: utf-8 -*-
# PyInstaller spec: one-file gst_master_importer.exe with the official HSN/SAC
# and CBIC rate seed bundled alongside so the frozen exe is self-contained.
# Build with:  pyinstaller gst_master_importer.spec

a = Analysis(
    ["gst_master_importer.py"],
    pathex=[],
    binaries=[],
    datas=[
        ("data/hsn_master.json", "data"),
        ("data/gst_rate_seed.json", "data"),
    ],
    hiddenimports=["psycopg2._psycopg"],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=["tkinter", "matplotlib", "IPython"],
    noarchive=False,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="gst_master_importer",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
