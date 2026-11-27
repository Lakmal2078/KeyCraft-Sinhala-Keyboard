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
- `si_legacy_keycraft.txt` is the frozen snapshot of the Sinhala literals used
  by the previously bundled dictionary. It comes from this project's GPL-3.0
  `SinhalaTransliterator.java` at commit
  `55795f69cfc6d2da4e26fdeda07cd6debd3b245c`. With the existing embedded core words
  and LibreOffice source, it reproduces the bundled `si.dict` byte-for-byte.
- Add newly reviewed entries to `si_reviewed.tsv`. Keep the source reference,
  redistribution license, and human review reference with each spelling.
  A row that overlaps the old vocabulary can add aliases or raise priority.
  The trie contains one NFC entry, weighted at the higher priority.
  The final TSV column is `status`. Only `APPROVED` rows contribute to either
  generated asset, and those rows require all provenance fields. `PENDING`
  and `REJECTED` rows still undergo spelling and format validation.
- `si_existing_priorities.tsv` adjusts editorial priorities for a small set
  of established everyday words. The generator verifies that each word
  already exists in the bundled sources. Use the reviewed TSV for additions.
- Run `python3 gen_sinhala_dict.py` to rebuild `assets/dict/si.dict` and
  `assets/dict/si_aliases.tsv` offline. The default build also refreshes the
  bundled Sinhala SHA-256 pin, which `--check` verifies. Aliases add candidates for exact Latin
  input in Singlish literal mode. The generator checks reference syntax. The
  reviewer verifies the references, spelling, and redistribution permission.


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
