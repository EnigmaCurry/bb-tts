#!/usr/bin/env bb

(require '[tts :refer [perform say dialog laugh breath sigh pause
                       M1 M2 M3 M4 M5 F1 F2 F3 F4 F5
                       with-speed]])

;; --- A simple knock-knock joke ---

(perform
  (dialog
    (M1 "Knock knock.")
    (F1 "Who's there?")
    (M1 "Babashka.")
    (F1 "Babashka who?")
    (M1 (laugh) "Babashka your pardon, I didn't mean to interrupt!")))

;; --- Narration with speed changes ---

(perform
  (say M3 (breath) "It was a dark and stormy night.")
  (say M3 "The wind howled through the trees.")
  (with-speed 0.8
    (say M3 "And then" (pause 1.0) "silence.")))

;; --- Multi-voice conversation ---

(perform
  (dialog
    (F2 "Did you hear? They made a text to speech system in babashka.")
    (M2 (breath) "No way. How does it sound?")
    (F2 "Listen for yourself!" (laugh))
    (M2 (sigh) "I have to admit, that's pretty impressive.")))
