# DataEater phone benchmarks — 7 October 2026

Device: **vivo V2453A (PD2453), Android 16, ARM64, SM8735**.
Kernel-reported total memory: approximately 11 GiB. Runtime: LiteRT-LM 0.17.1.
App: DataEater 1.2.0; Builder: 0.3.0.

| Model bundle | Download | Answer times in synthetic checks | Selection reason |
|---|---:|---:|---|
| Gemma 4 E2B | 2.01 GB | 1–2.5 seconds | Fast answers; passed supplied facts, follow-up, negation and missing-answer checks. Recommended starting point. |
| Qwen2.5 1.5B Instruct | 1.60 GB | 2–4.5 seconds | Smaller alternative with good document answers in these checks. |
| Qwen3 1.7B | 0.98 GB | 2–3 seconds | Smallest retained download; final checks passed, but an earlier corpus run gave an inconsistent refusal. Prefer simpler questions. |
| Qwen3 4B Instruct 2507 | 2.66 GB | 4–6.2 seconds | Larger alternative; passed document, memory and follow-up checks, with slower answers. |

These are observed ranges, not tokens-per-second measurements or repeated-run
statistical estimates. Load times are separate; first loads can be much slower.

All four answered the final FAA questions about Ohm’s law and the four-stroke
cycle correctly, then answered a question after generation cancellation.
The database contained 7,842 passages. Median search time across five queries
was 11.2–11.5 ms; index preparation took 1.59–1.73 seconds.

The synthetic checks covered supplied pressure, replacement intervals, three oil
pressure causes, a prohibited cleaning method, an absent torque value, Paris and
remembering a supplied identifier. The final Gemma/Qwen1.7 runs also checked
identifier recall after twelve intervening turns; earlier runs used seven cases.

| Removed download | Observed issue |
|---|---|
| Nemotron 3 Nano 4B | Standalone pressure answer worked; repeated conversations returned empty answers on CPU. |
| LFM2.5 1.2B | Inconsistent refusals and missed cleaning prohibition. |
| Qwen3 0.6B | Refused provided facts and repeated refusal text. |
| Qwen2 0.5B | Missed prohibition and invented a missing torque value. |
| SmolLM2 360M | Confused replacement intervals and invented a torque value in an earlier run. |
| Qwen3.5 0.8B | GPU load exceeded six minutes; CPU run invented torque and repeated units. |

These findings concern the tested conversions and this phone. They do not prove
that the model families fail on every runtime or device. Larger untested models
are deferred, not classified as broken.

247 app unit tests and nine builder test scripts passed. Signed release startup
and model selection were checked on the phone. Tests cover a small question set
on one device; they do not certify maintenance advice or all Android devices.
Verify important answers against the source document.
