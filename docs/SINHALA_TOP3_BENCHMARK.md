# Sinhala Top‑3 benchmark

මෙහි වාක්‍ය 100ක් ඇත. Direct Sinhala prefix 40ක්, Singlish literal input
40ක් සහ next-word prediction 20ක් ඇතුළත් වේ. ඒ අතර දෛනික වචන 60ක්,
නම් සහ ප්‍රදේශ 20ක්, chat සහ app භාවිතය 20ක් පරීක්ෂා වේ.

- `before` ක්ෂේත්‍රය next-word context ලෙස යොදා ගනී. `after` ක්ෂේත්‍රය
  වාක්‍යය කියවීමට උපකාරී වේ.
- Direct Sinhala rows සම්පූර්ණ වචනයට වඩා කෙටි prefix එකක් භාවිත කරයි.
  Singlish rows වල සාමාන්‍ය Latin prefixes සහ සම්පූර්ණ spellings දෙවර්ගයම ඇත.
- Keyboard strip එකේ පෙනෙන මුල් තීරු තුනෙන් එකක් NFC normalization පසු
  `expected` වචනයට සමාන නම් hit එකක් ලෙස ගණන් ගනී. Singlish literal
  spelling එක භාවිත කරන slot එකත් එම තීරු තුනට ඇතුළත්.
- සෑම row එකක්ම bundled dictionary සහ reviewed aliases සමඟ පරීක්ෂා වේ.
  Personal dictionary සහ ඉගෙනගත් history හිස්ව තබයි.
- වාක්‍ය benchmark සඳහා රචනා කළ test inputs වේ. ඒවා පරිශීලක telemetry
  හෝ independent Sinhala reviewer approval ලෙස නොසලකන්න.

```sh
./gradlew testDebugUnitTest --tests '*.SinhalaTopThreeBenchmarkTest' --info
```

`TOP3` rows හි hits, sample count සහ ප්‍රතිශතය ඇත. `MISS` rows හි fixture
row number, input, expected word, bundled exact lookup තත්ත්වය සහ
තිරයට ලැබුණු යෝජනා ඇත. Gradle XML report එකේත් මේ output එක සටහන් වේ.

Vocabulary review සඳහා exact lookup නැති වැදගත් වචන මුලින් බලන්න.
දැනට exact entry එක ඇති Singlish misses සඳහා සාමාන්‍ය Latin spelling
වලට alias අවශ්‍යද යන්න පරීක්ෂා කරන්න. Next-word misses සඳහා context සහ
prediction සලකා බලන්න. අනුමත spelling එක, මූලාශ්‍රය, බලපත්‍රය සහ reviewer
reference එක ලැබුණු පසු reviewed TSV එකට එක් කර මේ වාක්‍ය 100 මත නැවත මැන බලන්න.

## වත්මන් baseline

| Input mode | Top‑3 hits | අනුපාතය |
| --- | ---: | ---: |
| Direct Sinhala prefix | 23/40 | 57.5% |
| Singlish literal | 32/40 | 80.0% |
| Next-word prediction | 3/20 | 15.0% |
| එකතුව | 58/100 | 58.0% |

Chat සහ app වාක්‍ය 9/20 (45.0%), නම් සහ ප්‍රදේශ 15/20 (75.0%),
දෛනික වාක්‍ය 34/60 (56.7%) විය. Direct Sinhala misses 17න් 13ක
expected වචනය bundled dictionary එකෙන් exact lookup කළ නොහැකි විය.
ඉතිරි හතර `පාන්`, `පොත`, `ගුරුවරිය`, සහ `හොඳයි` යි. ඒවාට
prefix ranking review අවශ්‍යයි. Next-word misses 17න් 15කට exact
dictionary entries තිබුණා. ඒවාට contextual predictions review කරන්න.

[Draft review queue](sinhala-vocabulary-review-draft.tsv) තුළ අලුත් වචන
15ක් සහ දැනට ඇති වචන සඳහා alias යෝජනා 3ක් තිබේ. `benchmark_rows` යනු
header එක හැර ගණන් කළ දත්ත පේළි අංක වේ. Queue එක benchmark
misses මත සකස් කළ එකක්. `source`, `license`, `review` සම්පූර්ණ කළ
entries පමණක් [reviewed TSV](../srcs/dictionaries/si_reviewed.tsv) වෙත
ගෙන ගොස් `APPROVED` status එක සටහන් කරන්න. Next-word rows දෙකක් draft එකේ තිබේ. ඒ වචන එක් කිරීමෙන් පසුත්
next-word prediction එක වෙනම පරීක්ෂා කරන්න.

## Prefix ranking යාවත්කාලීන කිරීම

| Input mode | Baseline | යාවත්කාලීන build එක | වෙනස |
| --- | ---: | ---: | ---: |
| Direct Sinhala prefix | 23/40 | 27/40 | +4 |
| Singlish literal | 32/40 | 32/40 | 0 |
| Next-word prediction | 3/20 | 3/20 | 0 |
| එකතුව | 58/100 | 62/100 | +4 |

පැරණි Cdict frequency-first order එකේ `පාන්` 79 වැනි සහ `ගුරුවරිය`
14 වැනි completion විය. දෙකම 12-candidate සීමාවෙන් පිටත විය. `පොත`
4 වැනි සහ `හොඳයි` 3 වැනි completion වුවත් exact prefix එක පළමු
slot එක ගත් විට ඒවා මුල් තුනට නොපැමිණියේය.

Direct mode එක දැන් prefix ගැළපීම් තෝරා, exact සහ දැනටමත් එකතු වූ
candidates ඉවත් කර, සීමාවට පෙර priority සහ ඉතිරි අකුරු ගණන අනුව
order කරයි. `si_existing_priorities.tsv` දැනට ඇති දෛනික වචනවල
priority යාවත්කාලීන කරයි. මේ ධාවනය ප්‍රකාශිත baseline එකේ වාක්‍ය
100ම භාවිත කළා. CI එක ඒ baseline එකට පහළ overall හෝ input-mode
score එකක් තිබේ නම් test එක fail කර `TOP3_DELTA` rows පෙන්වයි.
