#!/usr/bin/env bb

;; Babel — bilingual glossolalia dialog with a somewhat intelligible story.
;; Two voices code-switch between English and nonsense, madlibs-style,
;; as if telling a story in a pidgin tongue over a droning soundscape.

(require '[tts :refer [perform render say pause laugh breath sigh
                       M3 F4 with-speed ensure-server *sox-effects* *speed*]]
         '[sfx :as sfx]
         '[babashka.process :as proc]
         '[clojure.java.io :as io])

(def render-file (first *command-line-args*))

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

;; --- English word pools (from madlibs phraseology) ---

(def nouns
  ["penguin" "robot" "wizard" "dragon" "pirate" "professor" "vampire"
   "mermaid" "goblin" "dinosaur" "unicorn" "ninja" "octopus" "walrus"
   "yeti" "flamingo" "jellyfish" "kangaroo" "hedgehog" "platypus"
   "elephant" "giraffe" "jackal" "scarecrow" "gargoyle"])

(def verbs-past
  ["launched" "devoured" "befriended" "juggled" "serenaded" "discovered"
   "hypnotized" "outsmarted" "enchanted" "levitated" "mesmerized"
   "obliterated" "vaporized" "wrangled" "zapped" "betrayed"
   "celebrated" "frightened" "hugged" "lassoed"])

(def verbs-present
  ["launches" "devours" "befriends" "juggles" "serenades" "discovers"
   "hypnotizes" "outsmarts" "enchants" "levitates" "mesmerizes"
   "obliterates" "vaporizes" "wrangles" "zaps" "betrays"
   "celebrates" "frightens" "hugs" "lassoes"])

(def adjectives
  ["fluffy" "enormous" "invisible" "suspicious" "radioactive" "furious"
   "majestic" "bewildered" "spectacular" "ferocious" "magnificent"
   "peculiar" "terrifying" "mischievous" "legendary" "thunderous"
   "volcanic" "whimsical" "ancient" "dazzling" "nefarious" "luminous"])

(def adverbs
  ["frantically" "elegantly" "suspiciously" "heroically" "accidentally"
   "dramatically" "mysteriously" "recklessly" "triumphantly"
   "wildly" "absurdly" "gleefully" "haphazardly" "defiantly"])

(def locations
  ["on the moon" "inside a volcano" "in the library"
   "under the bed" "in a bouncy castle" "on a pirate ship"
   "at the dentist" "inside a whale" "at a disco"
   "in a cave" "in a treehouse" "at a laundromat" "under a waterfall"])

;; --- Pidgin sentence generators ---
;; These mix English structure with glossolalia substitutions,
;; creating a half-intelligible pidgin.

(defn gnoun [] (if (< (rand) 0.4) (gword) (rand-nth nouns)))
(defn gadj [] (if (< (rand) 0.5) (gword) (rand-nth adjectives)))
(defn gverb [] (if (< (rand) 0.4) (gword) (rand-nth verbs-past)))
(defn gverb-now [] (if (< (rand) 0.4) (gword) (rand-nth verbs-present)))
(defn gadv [] (if (< (rand) 0.5) (gword) (rand-nth adverbs)))
(defn gloc [] (if (< (rand) 0.35) (str "in the " (gword)) (rand-nth locations)))

;; Sentence templates — English skeleton with glossolalia flesh
(def story-templates
  [;; declaration
   (fn [] (format "The %s %s %s %s the %s %s."
            (gadj) (gnoun) (gadv) (gverb) (gadj) (gnoun)))
   ;; question
   (fn [] (format "But why did the %s %s %s the %s %s?"
            (gadj) (gnoun) (gverb) (gadj) (gnoun)))
   ;; exclamation
   (fn [] (format "%s! The %s %s %s %s!"
            (gword) (gadj) (gnoun) (gadv) (gverb)))
   ;; because
   (fn [] (format "Because the %s %s was %s, %s, and %s."
            (gadj) (gnoun) (gword) (gword) (gword)))
   ;; location reveal
   (fn [] (format "And so the %s %s went %s, where a %s %s %s %s everything."
            (gadj) (gnoun) (gloc) (gadj) (gnoun) (gadv) (gverb)))
   ;; consequence
   (fn [] (format "After that, the %s was never the same. %s %s %s."
            (gnoun) (gword) (gword) (gword)))
   ;; pure glossolalia outburst
   (fn [] (gphrase (+ 4 (rand-int 6))))
   ;; agreement/disagreement
   (fn [] (format "%s, yes, the %s %s %s it."
            (gword) (gadj) (gnoun) (gadv) (gverb)))
   ;; oath/invocation
   (fn [] (format "By the %s of the %s %s! %s!"
            (gword) (gadj) (gnoun) (gword)))
   ;; plot twist
   (fn [] (format "But then, %s, a %s %s %s the %s %s %s."
            (gadv) (gadj) (gnoun) (gverb) (gadj) (gnoun) (gloc)))])

;; --- Dialog structure ---

(defn pan-segs [segs pan]
  (mapv #(if (map? %) (assoc % :pan pan) %) (flatten segs)))

(defn say-a [& parts] (pan-segs (apply say M3 parts) -0.7))
(defn say-b [& parts] (pan-segs (apply say F4 parts) 0.7))

(def speakers [say-a say-b])

;; Story acts — each act is a sequence of exchanges
(defn exchange
  "One speaker says a pidgin sentence."
  []
  (let [voice-fn (rand-nth speakers)
        sentence ((rand-nth story-templates))]
    (voice-fn sentence)))

(defn rapid-exchange
  "Quick back-and-forth, increasingly glossolalic."
  []
  (mapcat (fn [_]
            (concat (say-a ((rand-nth story-templates)))
                    (say-b ((rand-nth story-templates)))))
          (range (+ 3 (rand-int 3)))))

(defn slow-monologue
  "One voice tells a stretch of the story, slower and more contemplative."
  []
  (let [voice-fn (rand-nth speakers)]
    (vec (mapcat (fn [_] (voice-fn ((rand-nth story-templates))))
                 (range (+ 2 (rand-int 3)))))))

(defn unison-chant
  "Both voices say the same glossolalia phrase together."
  []
  (let [p (gphrase (+ 3 (rand-int 3)))]
    (concat (say-a p) (say-b p))))

(defn argument
  "Heated exchange — short bursts, fast, with interjections."
  []
  (mapcat (fn [_]
            (let [[first-voice second-voice] (shuffle speakers)]
              (concat (first-voice ((rand-nth story-templates)))
                      (second-voice (gphrase (+ 1 (rand-int 2)))))))
          (range (+ 3 (rand-int 3)))))

(defn whispered-aside
  "One voice drops to a slow aside, as if confiding a secret."
  []
  (let [voice-fn (rand-nth speakers)]
    (voice-fn (format "Listen... the %s %s... it %s the %s... %s..."
                (gadj) (gnoun) (gverb-now) (gnoun) (gword)))))

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
        ;; Act 3: Deeper translation — things get strange, more glossolalic
        act3 [tablet-entry
              whispered-aside
              (fn [] (concat
                       (say-b (format "Wait. That last form, %s, it appeared in the first chamber too."
                                (gword)))
                       (say-a (format "Yes! And look, when you combine it with %s..."
                                (gword)))
                       (say-b (format "It makes %s %s." (gword) (gword)))
                       (say-a (format "%s! Exactly." (gword)))))
              rapid-exchange
              exchange]
        ;; Act 4: Conjugation play — they start riffing on each other's words
        act-conj [conjugation-list conjugation-list
                  dueling-conjugations]
        ;; Act 5: Confrontation — argument over interpretation
        act5 [argument
              (fn [] (concat
                       (say-b (format "No, no. The %s form is %s, not %s!"
                                (gadj) (gword) (gword)))
                       (say-a (format "I'm telling you, the tablet says %s! The %s %s %s the %s!"
                                (gword) (gadj) (gnoun) (gverb) (gnoun)))
                       (say-b (laugh))
                       (say-b (format "%s! You're reading it upside down!" (gword)))))
              unison-chant rapid-exchange]
        ;; Act 6: Wrapping up
        act6 [(fn [] (concat
                       (say-b "We need to stop. Someone might be listening.")
                       (say-a (format "One more. The final tablet. Root word: %s." (gword)))
                       (say-b "Go.")))
              tablet-entry
              (fn [] (concat
                       (say-a (format "That's it. That's all twelve tablets. %s." (gword)))
                       (say-b "I have it all. Same time tomorrow?")
                       (say-a (format "%s. Tomorrow." (gword)))
                       (say-b "Be careful.")
                       [(pause 0.3)]
                       (say-a (gword))
                       [(pause 1.5)]))]
        all-sections (concat act1 act2 act3 act-conj act5 act6)]
    (vec (mapcat (fn [f] (concat (f) [(pause 0.6)])) all-sections))))

;; --- Main ---

(ensure-server)
;; Telephone bandpass on voices + slow down with pitch shift
(alter-var-root #'*sox-effects* (constantly ["speed" "0.85" "sinc" "300-3400"]))
;; Slowest speed for all voices
(alter-var-root #'*speed* (constantly 0.7))

(def tmpdir (System/getProperty "java.io.tmpdir"))
(def ring-file (str tmpdir "/babel_ring.wav"))
(def bg-file (str tmpdir "/babel_bg.wav"))

(println "Generating ringtone...")
(sfx/generate-ringtone 2 ring-file)

(println "Generating binaural beats...")
(sfx/generate-binaural bg-file 60
  :carrier 150 :beat-freq 6 :gain -18)

(if render-file
  (do
    (println (str "Rendering to " render-file " ..."))
    (let [voices-file (str tmpdir "/babel_voices.wav")
          intro-file (str tmpdir "/babel_intro.wav")]
      (apply render voices-file (build-story))
      @(proc/process ["sox" ring-file voices-file intro-file]
                     {:out :inherit :err (io/file "/dev/null")})
      ;; Loop bg to match intro duration
      (let [intro-dur (Double/parseDouble
                        (clojure.string/trim
                          (:out @(proc/process ["sox" "--info" "-D" intro-file]
                                              {:out :string}))))
            bg-looped (str tmpdir "/babel_bg_loop.wav")
            repeats (max 0 (int (Math/ceil (/ intro-dur 60))))]
        (println "Mixing voices with background...")
        @(proc/process ["sox" bg-file bg-looped "repeat" (str repeats)
                        "trim" "0" (str intro-dur) "fade" "0" (str intro-dur) "4"]
                       {:out :inherit :err (io/file "/dev/null")})
        @(proc/process ["sox" "-m" intro-file bg-looped render-file "norm"]
                       {:out :inherit :err (io/file "/dev/null")})
        (.delete (io/file bg-looped)))
      (doseq [f [voices-file intro-file ring-file bg-file]]
        (.delete (io/file f)))
      (println (str "Done: " render-file))))
  (do
    (println "=== Babel ===\n")
    ;; Play ringtone first, then start binaural + voices together
    @(proc/process ["paplay" ring-file] {:out :inherit :err :inherit})
    (def bg-player (proc/process
                     ["bash" "-c"
                      (str "sox " bg-file " -t raw -r 44100 -c 2 -e signed -b 16 - repeat 100"
                           " | paplay --raw --format=s16le --rate=44100 --channels=2")]
                     {:out :inherit :err (io/file "/dev/null")}))
    (apply perform (build-story))
    (future (Thread/sleep 3000) (.destroy (:proc bg-player)))
    @bg-player
    (doseq [f [ring-file bg-file]]
      (.delete (io/file f)))))
