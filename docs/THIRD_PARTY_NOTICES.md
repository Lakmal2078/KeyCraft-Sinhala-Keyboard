# Third-party notices

KeyCraft is licensed under GNU GPL-3.0-only. The following bundled or source
materials retain their respective upstream notices and licenses.

| Material | Source / attribution | License |
| --- | --- | --- |
| Base keyboard implementation | [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard), Julow and contributors | GPL-3.0-only |
| Sinhala word list (`assets/dict/si.dict`) | LibreOffice `si_LK.dic`; © 2010 Laknath Semage | GPL-3.0-or-later; original notice in `srcs/dictionaries/LICENSES-en.txt` |
| English (US) word list (`assets/dict/en_US.dict`) | OpenBoard `v1.4.5` `en_wordlist.combined.gz` | GPL-3.0; original license in `srcs/dictionaries/OPENBOARD-LICENSE` |
| Layout definitions | `srcs/layouts` | CC0-1.0; see `srcs/layouts/LICENSE` |
| Compose sequences / X11 key symbols | X.Org / The Open Group and Digital Equipment Corporation; notices remain in source files | Permissive license terms included in `srcs/compose/compose/keysymdef.h` |
| Font Awesome artwork | Font Awesome Free 6.1.0, Fonticons, Inc. | CC BY 4.0 for icons; source notices retained in `srcs/special_font` |
| Roboto glyph artwork | Google / Material Design assets | Apache-2.0; source notice retained in `srcs/special_font` |
| AndroidX dependencies | AndroidX libraries | Apache-2.0 |

The `vendor/cdict` Git submodule is retained for historical/reference purposes;
the Android application uses the pure-Java reader in `srcs/java/juloo/cdict`
and does not package the submodule's native code.
