#!/usr/bin/env bb

;; Babel — bilingual glossolalia dialog with a somewhat intelligible story.
;; Two voices code-switch between English and nonsense, madlibs-style,
;; as if telling a story in a pidgin tongue over a droning soundscape.

(require '[tts :refer [perform render say pause laugh breath sigh
                       M3 F4 ensure-server *sox-effects* *pronunciations*]]
         '[sfx :as sfx]
         '[babashka.process :as proc]
         '[clojure.java.io :as io])

(def render-file (when-let [f (first *command-line-args*)]
                   (.getAbsolutePath (io/file f))))

;; --- Glossolalia generator (from incantation dialect) ---

(def onsets
  ["z" "k" "th" "v" "sh" "d" "g" "b" "m" "kh"
   "f" "r" "p" "t" "s" "l" "n" "h" "w" "j"
   "ch" "dr" "gl" "br" "fr" "tr" "pr" "kr" "zh" "sk"])

(def vowels
  ["a" "e" "i" "o" "u" "ah" "oh" "ee" "oo" "ai"
   "ei" "au" "ay" "ey" "uh" "aa" "oi"])

(def endings
  ["" "" "" "" "" "" "n" "m" "r" "l" "s" "th" "sh"])

(defn syllable []
  (str (rand-nth onsets) (rand-nth vowels) (rand-nth endings)))

(defn gword []
  (apply str (repeatedly (inc (rand-int 3)) syllable)))

(def pair-endings
  ["ah" "en" "oth" "ul" "een" "az" "im" "esh" "oon" "ik" "el" "um"])

(defn gpair
  "Generate a root word with two different endings, like near-cognates."
  []
  (let [root (apply str (repeatedly (inc (rand-int 2)) syllable))
        [e1 e2] (take 2 (shuffle pair-endings))]
    (str root e1 " " root e2)))

(defn gphrase [n]
  (clojure.string/join " "
    (mapcat (fn [_]
              (if (< (rand) 0.3)
                [(gpair)]
                [(gword)]))
            (range n))))

;; --- Translation dialog templates ---
;; These fit the premise: two researchers on a phone call translating
;; alien tablets. English structure degrades as the alien language
;; seeps into their speech.

;; Tablet reading — pure alien passages
(defn tablet-passage []
  (gphrase (+ 5 (rand-int 8))))

;; Dialog templates for the translation context
(def translation-templates
  [;; reading a passage
   (fn [] (format "The next line reads: %s." (tablet-passage)))
   ;; cross-referencing
   (fn [] (format "That matches the third tablet. %s, then %s." (gword) (gword)))
   ;; noticing a pattern
   (fn [] (format "Wait, %s appears again. That's the fourth time." (gword)))
   ;; attempting to translate
   (fn [] (format "I think %s means something like... movement? Or passage?" (gword)))
   ;; correcting
   (fn [] (format "No, look at the suffix. %s is the active form. %s is passive." (gword) (gword)))
   ;; excitement
   (fn [] (format "Do you see it? %s %s %s! It's a complete sentence!" (gword) (gword) (gword)))
   ;; reading pure alien
   (fn [] (tablet-passage))
   ;; context from the dig
   (fn [] (format "This was carved deeper than the others. %s %s." (gword) (gword)))
   ;; language creeping in
   (fn [] (format "The structure is... %s. I don't know how else to say it. %s." (gword) (gword)))
   ;; doubt
   (fn [] (format "Unless %s is a proper noun. Then this whole section changes meaning." (gword)))
   ;; realization
   (fn [] (format "%s. That's not a word, it's a name. %s." (gword) (gword)))
   ;; paranoia
   (fn [] (format "Did you hear that? ... Never mind. The next glyph is %s." (gword)))])

;; --- Dialog structure ---

(defn pan-segs [segs pan]
  (mapv #(if (map? %) (assoc % :pan pan) %) (flatten segs)))

(defn say-a [& parts] (pan-segs (apply say M3 parts) -0.7))
(defn say-b [& parts] (pan-segs (apply say F4 parts) 0.7))

(def speakers [say-a say-b])

;; --- Dialog structure ---

(defn exchange
  "One speaker says a translation-context line."
  []
  (let [voice-fn (rand-nth speakers)]
    (voice-fn ((rand-nth translation-templates)))))

(defn rapid-exchange
  "Quick back-and-forth — cross-referencing tablets."
  []
  (mapcat (fn [_]
            (concat (say-a ((rand-nth translation-templates)))
                    (say-b ((rand-nth translation-templates)))))
          (range (+ 3 (rand-int 3)))))

(defn read-passage
  "One voice reads a long alien passage, the other reacts."
  []
  (let [[reader reactor] (shuffle [say-a say-b])]
    (concat
      (reader (tablet-passage))
      (reactor (rand-nth [(format "Again? Read it again." )
                          (format "%s... yes, go on." (gword))
                          (format "That changes everything.")
                          (format "The same root as before. %s." (gword))])))))

(defn unison-reading
  "Both voices read the same alien phrase together — as if chanting."
  []
  (let [p (gphrase (+ 3 (rand-int 3)))]
    (concat (say-a p) (say-b p))))

(defn debate
  "Heated exchange — disagreeing on interpretation."
  []
  (mapcat (fn [_]
            (let [[first-voice second-voice] (shuffle [say-a say-b])]
              (concat (first-voice ((rand-nth translation-templates)))
                      (second-voice (rand-nth [(format "No. That's %s, not %s." (gword) (gword))
                                               (format "You're misreading the suffix.")
                                               (format "Look at the glyph again. %s." (gword))
                                               (gphrase (+ 1 (rand-int 2)))])))))
          (range (+ 3 (rand-int 3)))))

(defn whispered-aside
  "One voice drops to a hushed tone, as if paranoid."
  []
  (let [voice-fn (rand-nth [say-a say-b])]
    (voice-fn (format "Listen... the %s inscription... it says %s... %s..."
                (gword) (gword) (gword)))))

;; --- Conjugation suffixes for nonsense declension ---
(def conjugation-suffixes
  ["-ah" "-en" "-oth" "-ul" "-een" "-az" "-im" "-esh"
   "-oon" "-ik" "-ara" "-eth" "-um" "-osh" "-ani" "-el"])

(defn conjugate
  "Take a root word and produce N conjugated forms."
  [root n]
  (let [suffixes (take n (shuffle conjugation-suffixes))]
    (mapv #(str root %) suffixes)))

(defn tablet-entry
  "Female introduces a root word, male gives all 12 conjugations."
  []
  (let [root (apply str (repeatedly (inc (rand-int 2)) syllable))
        forms (conjugate root 12)
        groups (partition-all 4 forms)]
    (concat
      (say-b (format "Next entry. The root word is: %s." root))
      [(pause 0.3)]
      (mapcat (fn [group]
                (say-a (str (clojure.string/join ", " group) ".")))
              groups)
      [(pause 0.2)]
      (say-b (format "%s. Noted." root)))))

(defn conjugation-list
  "One speaker recites a conjugation list of a nonsense word."
  []
  (let [voice-fn (rand-nth speakers)
        root (gword)
        forms (conjugate root (+ 4 (rand-int 4)))
        listing (clojure.string/join ". " forms)]
    (concat
      (voice-fn (str root ":"))
      (voice-fn (str listing ".")))))

(defn dueling-conjugations
  "Both speakers conjugate, then play off each other's words."
  []
  (let [root-a (gword)
        root-b (gword)
        forms-a (conjugate root-a (+ 3 (rand-int 3)))
        forms-b (conjugate root-b (+ 3 (rand-int 3)))]
    (concat
      ;; A recites their conjugation
      (say-a (str root-a ": " (clojure.string/join ", " forms-a) "."))
      [(pause 0.3)]
      ;; B recites theirs
      (say-b (str root-b ": " (clojure.string/join ", " forms-b) "."))
      [(pause 0.3)]
      ;; Now they play — echoing and mixing each other's forms
      (say-a (str (rand-nth forms-b) "? " (rand-nth forms-a) "!"))
      (say-b (str (rand-nth forms-a) "! " (rand-nth forms-b) ", " (rand-nth forms-b) "!"))
      (say-a (str (rand-nth forms-b) ", " (rand-nth forms-a) ", " (rand-nth forms-b) "..."))
      (say-b (laugh))
      (say-b (str (rand-nth forms-a) "! " (rand-nth forms-a) "!"))
      (say-a (str (rand-nth forms-b) ". " (rand-nth forms-a) ". "
                  (rand-nth forms-b) ". " (rand-nth forms-a) "."))
      (say-b (str (rand-nth forms-a) ", " (rand-nth forms-b) "!"))
      (let [shared (str (rand-nth forms-a) " " (rand-nth forms-b))]
        (concat (say-a shared) (say-b shared))))))

;; --- Build the story ---

(defn build-story []
  (let [;; Act 1: Phone pickup — clandestine contact
        act1 [(fn [] (concat
                       (say-b "Hello?")
                       [(pause 0.4)]
                       (say-a "It's me. Are you on a secure line?")
                       (say-b "Yes. Go ahead.")
                       [(pause 0.3)]
                       (say-a "I've been with the tablets all night. The second chamber inscription... it's not what we expected.")
                       (say-b "What do you mean?")
                       (say-a (format "There's a whole declension system. Every root has twelve forms. The first root is %s, but it branches into... everything."
                                (gword)))
                       (say-b "Twelve forms? Start from the beginning. I'll record.")))]
        ;; Act 2: Tablet translation — she introduces roots, he conjugates
        act2 [tablet-entry tablet-entry
              (fn [] (concat
                       (say-b "Are you seeing a pattern?")
                       (say-a (format "Yes. The suffixes map to... %s, %s, %s. Like tense, but also direction. And maybe intent."
                                (gword) (gword) (gword)))
                       (say-b "Intent?")))
              tablet-entry tablet-entry
              (fn [] (concat
                       (say-a (format "This one is different. The root %s, it appears on both tablets, but conjugated opposite ways."
                                (gword)))
                       (say-b "Read them both.")))]
        ;; Act 3: Deeper translation — noticing connections, reading passages
        act3 [tablet-entry
              read-passage
              (fn [] (concat
                       (say-b (format "Wait. That last form, %s, it appeared in the first chamber too."
                                (gword)))
                       (say-a (format "Yes! And look, when you combine it with %s..."
                                (gword)))
                       (say-b (format "It makes %s %s." (gword) (gword)))
                       (say-a (format "%s! Exactly." (gword)))))
              whispered-aside
              read-passage exchange]
        ;; Act 4: Conjugation play — they start riffing on each other's words
        act-conj [conjugation-list conjugation-list
                  dueling-conjugations]
        ;; Act 5: Argument over interpretation
        act5 [debate
              (fn [] (concat
                       (say-b (format "No, no. The %s form is %s, not %s!"
                                (gword) (gword) (gword)))
                       (say-a (format "I'm telling you, the tablet says %s! %s %s %s!"
                                (gword) (gword) (gword) (gword)))
                       (say-b (laugh))
                       (say-b (format "%s! You're reading it upside down!" (gword)))))
              unison-reading rapid-exchange]
        ;; Act 6: The language takes over — mostly alien, fragments of English
        act6 [read-passage
              (fn [] (concat
                       (say-a (format "I can't... the words are... %s. I think in %s now." (gword) (gword)))
                       (say-b (format "%s. I know. Me too." (gword)))))
              unison-reading]
        ;; Act 7: Wrapping up — paranoia, sign off
        act7 [(fn [] (concat
                       (say-b "We need to stop. Someone might be listening.")
                       (say-a (format "One more. The final tablet. Root word: %s." (gword)))
                       (say-b "Go.")))
              tablet-entry
              (fn [] (concat
                       (say-a (format "That's it. That's all of them. %s." (gword)))
                       (say-b "I have it all. Same time tomorrow?")
                       (say-a (format "%s. Tomorrow." (gword)))
                       (say-b "Be careful.")
                       [(pause 0.3)]
                       (say-a (gword))
                       [(pause 1.5)]))]
        all-sections (concat act1 act2 act3 act-conj act5 act6 act7)]
    (vec (mapcat (fn [f] (concat (f) [(pause 0.6)])) all-sections))))

;; --- Main ---

(ensure-server)
;; Telephone bandpass on voices
(alter-var-root #'*sox-effects* (constantly ["sinc" "200-5000"]))
(alter-var-root #'*pronunciations* (constantly {"record" "rekord"}))

(def tmpdir (System/getProperty "java.io.tmpdir"))
(def ring-file (str tmpdir "/babel_ring.wav"))

(println "Generating ringtone...")
(sfx/generate-ringtone 2 ring-file)

(if render-file
  (do
    (let [voices-file (str tmpdir "/babel_voices.wav")
          intro-file (str tmpdir "/babel_intro.wav")]
      (println (str "Rendering to " render-file " ..."))
      (println (str "Voices temp file: " voices-file))
      (apply render voices-file (build-story))
      @(proc/process ["sox" ring-file voices-file render-file]
                     {:out :inherit :err :inherit})
      (doseq [f [voices-file intro-file ring-file]]
        (when (.exists (io/file f)) (.delete (io/file f))))
      (println (str "Done: " render-file))))
  (do
    (println "=== Babel ===\n")
    @(proc/process ["paplay" ring-file] {:out :inherit :err :inherit})
    (apply perform (build-story))
    (.delete (io/file ring-file))))
