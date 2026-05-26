#!/usr/bin/env bb

;; Babel — bilingual glossolalia dialog with a somewhat intelligible story.
;; Two voices code-switch between English and nonsense, madlibs-style,
;; as if telling a story in a pidgin tongue over a droning soundscape.

(require '[tts :refer [perform render say pause laugh breath sigh
                       M3 F4 with-speed ensure-server *sox-effects*]]
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

(defn gphrase [n]
  (clojure.string/join " " (repeatedly n gword)))

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
  (with-speed 1.1
    (mapcat (fn [_]
              (concat (say-a ((rand-nth story-templates)))
                      (say-b ((rand-nth story-templates)))))
            (range (+ 3 (rand-int 3))))))

(defn slow-monologue
  "One voice tells a stretch of the story, slower and more contemplative."
  []
  (let [voice-fn (rand-nth speakers)]
    (with-speed 0.85
      (vec (mapcat (fn [_] (voice-fn ((rand-nth story-templates))))
                   (range (+ 2 (rand-int 3))))))))

(defn unison-chant
  "Both voices say the same glossolalia phrase together."
  []
  (let [p (gphrase (+ 3 (rand-int 3)))]
    (with-speed 0.8
      (concat (say-a p) (say-b p)))))

(defn argument
  "Heated exchange — short bursts, fast, with interjections."
  []
  (with-speed 1.2
    (mapcat (fn [_]
              (let [[first-voice second-voice] (shuffle speakers)]
                (concat (first-voice ((rand-nth story-templates)))
                        (second-voice (gphrase (+ 1 (rand-int 2)))))))
            (range (+ 3 (rand-int 3))))))

(defn whispered-aside
  "One voice drops to a slow aside, as if confiding a secret."
  []
  (let [voice-fn (rand-nth speakers)]
    (with-speed 0.75
      (voice-fn (format "Listen... the %s %s... it %s the %s... %s..."
                  (gadj) (gnoun) (gverb-now) (gnoun) (gword))))))

;; --- Build the story ---

(defn build-story []
  (let [;; Act 1: Phone pickup and opening — voice B answers, A launches into pidgin
        act1 [(fn [] (concat
                       (say-b "Hello?")
                       [(pause 0.4)]
                       (say-a (format "%s! %s, %s %s!"
                                (gword) (gword) (gadj) (gnoun)))
                       (say-b (format "Who is this? What do you %s?"
                                (gword)))
                       [(pause 0.3)]
                       (say-a (format "Listen, the %s %s, it %s the %s."
                                (gadj) (gnoun) (gverb-now) (gnoun)))
                       (say-b (format "The %s? %s?"
                                (gnoun) (gword)))))
              exchange exchange]
        ;; Act 2: Rising action — things get weird
        act2 [rapid-exchange slow-monologue exchange
              whispered-aside exchange]
        ;; Act 3: Confrontation — heated and chaotic
        act3 [argument unison-chant rapid-exchange
              (fn [] (with-speed 0.9
                       (concat
                         (say-a (format "The %s %s %s %s the %s!"
                                  (gadj) (gnoun) (gadv) (gverb) (gnoun)))
                         (say-b (laugh))
                         (say-b (format "%s! %s %s!"
                                  (gword) (gword) (gword))))))]
        ;; Act 4: Wrapping up — summarizing, winding down
        act4 [exchange
              (fn [] (with-speed 0.95
                       (concat
                         (say-a (format "So, the %s %s %s the %s. %s."
                                  (gadj) (gnoun) (gverb) (gnoun) (gword)))
                         (say-b (format "Right, right. And the %s %s, it %s."
                                  (gadj) (gnoun) (gword)))
                         (say-a (format "Exactly. %s %s %s."
                                  (gword) (gword) (gword))))))
              ;; Act 5: Goodbye
              (fn [] (with-speed 0.85
                       (concat
                         (say-b (format "Okay, I have to go. %s %s."
                                  (gword) (gword)))
                         (say-a (format "Yes, yes. %s. Talk soon."
                                  (gword)))
                         (say-b (format "%s. Goodbye." (gword)))
                         [(pause 0.3)]
                         (say-a "Goodbye.")
                         [(pause 1.5)])))]
        all-sections (concat act1 act2 act3 act4)]
    (vec (mapcat (fn [f] (concat (f) [(pause 0.6)])) all-sections))))

;; --- Main ---

(ensure-server)
;; Telephone bandpass on voices
(alter-var-root #'*sox-effects* (constantly ["sinc" "300-3400"]))

(def tmpdir (System/getProperty "java.io.tmpdir"))
(def ring-file (str tmpdir "/babel_ring.wav"))
(def bg-file (str tmpdir "/babel_bg.wav"))

(println "Generating ringtone...")
(sfx/generate-ringtone 2 ring-file)

(println "Generating modem soundscape...")
(let [loop-dur 60
      modem-file (sfx/generate-modem loop-dur (sfx/tmp "babel_modem")
                   :element-count (int (* loop-dur 0.4))
                   :gap-min 0.5 :gap-max 3.0)]
  (sfx/mix [[modem-file -8]]
           bg-file
           :fade-in 2 :fade-out 2 :duration loop-dur :gain -4)
  (.delete (io/file modem-file)))

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
    (def bg-player (proc/process ["sox" bg-file "-t" "pulseaudio" "" "repeat" "100"]
                                 {:out :inherit :err (io/file "/dev/null")}))
    ;; Play ringtone, then stream voices live
    @(proc/process ["paplay" ring-file] {:out :inherit :err :inherit})
    (apply perform (build-story))
    (future (Thread/sleep 3000) (.destroy (:proc bg-player)))
    @bg-player
    (doseq [f [ring-file bg-file]]
      (.delete (io/file f)))))
