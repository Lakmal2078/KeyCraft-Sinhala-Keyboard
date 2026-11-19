# Dictionary source data

The checked-in dictionary source files make the bundled word lists auditable and
keep Sinhala dictionary generation independent of network access.

## Sinhala

- `si_LK.dic` is the LibreOffice Sinhala (`si_LK`) Hunspell word list.
- Original copyright: © 2010 Laknath Semage.
- License: GNU GPL version 3 or (at your option) any later version. The original
  `LICENSES-en.txt` is preserved alongside the source.
- Upstream: <https://github.com/LibreOffice/dictionaries/tree/master/si_LK>
- GitHub blob SHA for the imported `si_LK.dic`:
  `11a46f977ddc4b342ac0df7a30d4dc5eb48565d2`.
  SHA-256: `d6ce8cef2bbf184459bb3073d2ddc246afa914efaf8fc438b32e8d7724abfcfd`
- `gen_sinhala_dict.py` combines this word list with the project’s Sinhala
  transliteration vocabulary and embedded common words to generate
  `assets/dict/si.dict`.

## English (United States)

- `en_wordlist.combined` is the English (US) source word list from OpenBoard
  tag `v1.4.5`, at `dictionaries/en_wordlist.combined.gz` (decompressed here).
- The OpenBoard source repository is licensed under GNU GPL version 3; its
  license text is preserved as `OPENBOARD-LICENSE`.
  SHA-256: `73308519ff089c73baa35e08f25087bb62ed4a6bf2f96af3efb402187f7c9edb`
- The upstream dictionary collection identifies this OpenBoard word list as
  the source for its legacy English (US) dictionary.
- Upstream: <https://github.com/openboard-team/openboard/tree/v1.4.5/dictionaries>

`assets/dict/en_US.dict` is the compiled dictionary used by the application.
The source word list and upstream license are retained here for attribution and
source availability.
