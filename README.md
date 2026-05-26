# bb-tts

Text-to-speech powered by [Supertonic](https://github.com/supertone-inc/supertonic-py) and scripted with [Babashka](https://babashka.org). Runs entirely on-device using CPU inference.

Includes a CLI tool and a Clojure DSL for composing multi-voice speech with expressions, pauses, and streaming playback.

## Requirements

- [Nix](https://nixos.org) with flakes enabled
- PulseAudio or PipeWire (for `paplay`)
- x86_64 Linux

## Quick start

Speak something immediately (auto-starts the TTS server on first run):

```bash
nix run github:EnigmaCurry/bb-tts -- say "Hello from babashka!"
```

## Install

```bash
nix profile install github:EnigmaCurry/bb-tts
bb-tts say "Now it's on your PATH."
```

## CLI usage

```
bb-tts say <text>       Synthesize and play text
bb-tts say --voice F1 "Hello"
bb-tts say --speed 0.8 --lang en "Slow and steady."
bb-tts voices           List available voices (M1-M5, F1-F5)
bb-tts health           Check server status
bb-tts serve            Start server in foreground
bb-tts stop             Stop daemonized server
bb-tts --debug say "Show timing info"
```

## DSL

The `tts` library lets you compose speech programmatically:

```clojure
#!/usr/bin/env bb
(require '[tts :refer [perform say dialog laugh breath sigh pause
                       M1 M2 M3 F1 F2 with-speed]])

;; Simple speech
(perform
  (say M1 "Hello world."))

;; Multi-voice dialog
(perform
  (dialog
    (M1 "Knock knock.")
    (F1 "Who's there?")
    (M1 "Babashka.")
    (F1 "Babashka who?")
    (M1 (laugh) "Babashka your pardon!")))

;; Expressions, pauses, and speed control
(perform
  (say M3 (breath) "It was a dark and stormy night.")
  (pause 1.0)
  (with-speed 0.8
    (say M3 "And then" (pause 0.5) "silence.")))

;; Programmatic generation
(doseq [[voice name] [[M1 "alpha"] [F1 "beta"] [M2 "gamma"]]]
  (perform (say voice (str "System " name " is online."))))
```

Run DSL scripts with the library on the classpath:

```bash
# From a local clone
nix develop
bb -cp lib my-script.clj

# Or run the included demos
nix run github:EnigmaCurry/bb-tts#demo
```

## DSL reference

### Voices

`M1` `M2` `M3` `M4` `M5` `F1` `F2` `F3` `F4` `F5`

Each is a function that takes text and expressions and returns speech segments.

### Expressions

| Function | Effect |
|----------|--------|
| `(laugh)` | Laughter |
| `(breath)` | Breath sound |
| `(sigh)` | Sigh |
| `(pause 1.0)` | Silence (seconds) |

Expressions split the text into separate segments for best quality.

### Composition

| Form | Description |
|------|-------------|
| `(say M1 "text" (laugh) "more")` | Build segments with a voice |
| `(dialog (M1 "hi") (F1 "hello"))` | Sequence of segments |
| `(with-speed 0.8 (say M1 "slow"))` | Override speed (0.7-2.0) |
| `(with-voice "M1" (say "text"))` | Override default voice |
| `(with-lang "es" (say M1 "hola"))` | Override language |
| `(perform ...)` | Synthesize and stream playback |

### Audio pipeline

Each segment is automatically:
1. Synthesized via the Supertonic HTTP server
2. Trimmed of leading/trailing silence
3. Normalized to consistent volume (expression tags excluded)
4. Streamed to PulseAudio as segments are ready

## Server management

The TTS server auto-starts as a background daemon on first use. The model (~200MB) downloads automatically from Hugging Face on first run.

```bash
bb-tts serve            # Run in foreground (see logs)
bb-tts stop             # Kill the background daemon
bb-tts health           # Check if running
```

## Supported languages

Supertonic supports 31 languages. Pass the language code via `--lang` (CLI) or `with-lang` (DSL):

`en` `es` `fr` `de` `it` `pt` `nl` `pl` `ru` `uk` `cs` `sk` `hu` `ro` `bg` `hr` `sr` `sl` `da` `no` `sv` `fi` `el` `tr` `ar` `he` `hi` `ja` `ko` `zh` `th`
