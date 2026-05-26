#!/usr/bin/env bb

(require '[tts :refer [perform say dialog laugh breath sigh pause
                       M1 M2 M3 M4 M5 F1 F2 F3 F4 F5
                       with-speed *pronunciations*]])

(alter-var-root #'*pronunciations* (constantly
  {"REPL"    "repple"
   "Clojure" "closure"
   "nix"     "nicks"}))

;; A procedurally generated podcast episode

(def hosts {:alice F1 :bob M1})
(def expert M3)

(defn host [who & parts]
  (apply (hosts who) parts))

(defn topic-segment [n topic explanation]
  (dialog
    (host :alice (str "Topic number " n "." ) (breath) topic)
    (host :bob explanation)
    (pause 0.3)))

(perform
  ;; Intro
  (host :alice "Welcome to the Babashka Podcast, episode one!")
  (host :bob (laugh) "Thanks for tuning in everyone.")
  (host :alice "Today we have ten topics to cover, so let us get started.")
  (pause 0.5)

  ;; Generated topics
  (topic-segment 1
    "What is Babashka?"
    "Babashka is a fast starting scripting environment for Clojure. It uses the small clojure interpreter to run clojure code without the JVM startup time.")

  (topic-segment 2
    "Why text to speech?"
    "Text to speech lets you turn any script into an audio experience. Imagine generating podcasts, audio books, or accessibility tools, all from code.")

  (topic-segment 3
    "The Supertonic engine."
    "Supertonic is a ninety nine million parameter model that runs entirely on your CPU. No cloud API needed, no GPU required.")

  (topic-segment 4
    "Expression tags."
    "You can add natural expressions like laughter, breathing, and sighs directly into your speech. It makes the output feel much more human.")

  (topic-segment 5
    "Nix flakes for packaging."
    "We packaged everything as nix flakes. That means you can install and run it with a single command on any nix system. Reproducible builds for the win.")

  (topic-segment 6
    "The clojure DSL."
    "The domain specific language lets you compose speech with multiple voices, pauses, and expressions. It is just data, so you can generate it programmatically.")

  (topic-segment 7
    "Audio processing pipeline."
    "Each clip gets trimmed of silence, normalized to a consistent volume, and concatenated into a single audio file before playback. All in babashka.")

  (topic-segment 8
    "Performance considerations."
    "The synthesis runs on a local HTTP server. Babashka handles the orchestration, WAV concatenation, and audio normalization. ByteBuffers keep it fast.")

  (topic-segment 9
    "Multi voice dialogues."
    "You can script conversations between any of the ten built in voices. Five male and five female, each with their own character.")

  (topic-segment 10
    "What is next?"
    "Streaming playback, more expression tags, and maybe even live narration of git diffs. The possibilities are endless.")

  (pause 0.5)

  ;; Outro with guest
  (host :alice "And that wraps up our ten topics!")
  (host :bob "Before we go, let us hear from our special guest.")
  (apply expert ["Thank you for having me." (sigh) "It has been a pleasure discussing the future of programmatic speech synthesis."])
  (host :alice "Thanks everyone for listening!")
  (host :bob (laugh) "See you next time on the Babashka Podcast!")

  ;; Countdown
  (pause 0.5)
  (say F2 "This podcast was generated entirely from code.")
  (say F2 "No human voices were used in the making of this episode."))
