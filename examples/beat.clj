#!/usr/bin/env bb

;; Bouncy nonsense-rap chants with repetitive syllables.

(require '[tts :refer [perform say pause breath laugh
                       M1 M2 M3 M4 M5 F1 F2 F3 F4 F5
                       with-speed]])

;; --- Syllable building blocks ---

(def doubles
  ["flim flam" "zim zam" "bim bam" "wim wam" "dim dam" "gim gam"
   "tip tap" "clip clap" "flip flap" "drip drap" "skip skap" "snip snap"
   "tick tock" "click clock" "brick brock" "flick flock" "trick trock" "nick nock"
   "bing bong" "ding dong" "ping pong" "king kong" "zing zong" "ring rong"
   "hip hop" "bip bop" "dip dop" "zip zop" "kip kop" "rip rop"
   "yip yap" "zib zab" "gob gab" "nib nab" "rib rab" "jib jab"])

(def triplets
  ["boppa loppa loo" "skibba dibba doo" "zibble zabble zoo"
   "tikka tikka tah" "rikka rikka rah" "chikka chikka cha"
   "wibble wobble woo" "fribble frabble froo" "dribble drabble droo"
   "gobba gobba goo" "lobba lobba loo" "robba robba roo"
   "diddle daddle dum" "fiddle faddle fum" "riddle raddle rum"
   "noodle doodle noo" "toodle loodle loo" "boodle woodle boo"
   "murka lurka lee" "chirpa jerka jee" "slurpa burpa bee"
   "frabba dabba day" "grabba jabba jay" "crabba tabba tay"
   "snorgle borgle bop" "florgle gorgle gop" "zorgle morgle mop"])

(def quad-rhymes
  ["wobble in the stew" "gobble gobble goo" "tumble in a stack"
   "bounce it right back" "shuffle with the feet" "wobble on the beat"
   "make the whole floor move" "got the grobble in the groove"
   "dancing in the shade" "yonder in the glade" "loop it once more"
   "stomp across the floor" "wiggle that phrase" "lost in a maze"
   "never gonna stop" "till the beat go pop" "let it all unjuice"
   "knees go loose" "bass go bum" "beat go drum"
   "back to the top" "don't you stop" "zoom zoom zoom"
   "boom boom boom" "done done done" "here we come"])

(def call-outs
  ["Womp-a-lomp!" "Ziggity-zomp!" "Chikka chomp!" "Rikka romp!"
   "Sproink!" "Skrrt!" "Boing boing!" "Plonk!"
   "Here we go!" "One more time!" "Bounce bounce!" "Let's go!"])

(def endings
  ["now repeat, repeat" "and the crowd go wild" "loop it again"
   "ten outta ten" "from the top" "one more round"
   "that's the sound" "let it pound" "hit the ground"
   "round and round" "never let it down" "shake the town"])

;; --- Verse construction ---

(defn pick [coll] (rand-nth coll))

(defn chant-line []
  (rand-nth
    [(str (pick doubles) ", " (pick doubles) ", " (pick quad-rhymes))
     (str (pick triplets) ", " (pick quad-rhymes))
     (str (pick doubles) ", " (pick triplets) ", " (pick quad-rhymes))
     (str (pick triplets) ", " (pick triplets))]))

(defn call-response-pair []
  [(str (pick call-outs))
   (str (pick triplets) " with the " (pick quad-rhymes))])

(defn make-bounce-verse []
  (vec (repeatedly 8 chant-line)))

(defn make-call-response-verse []
  (vec (mapcat identity (repeatedly 4 call-response-pair))))

;; --- Songs ---

(def songs
  [{:title "Flim-Flam Bounce"
    :voice M3
    :speed 1.1
    :make  make-bounce-verse}
   {:title "Snicker-Snack Trap"
    :voice F1
    :speed 1.15
    :make  make-bounce-verse}
   {:title "Bimble Bop"
    :voice M1
    :speed 1.05
    :make  make-bounce-verse}
   {:title "Womp-a-Lomp"
    :voice M5
    :speed 1.0
    :make  make-call-response-verse}
   {:title "Nonsense Cypher"
    :voice F4
    :speed 1.2
    :make  make-bounce-verse}])

(println "=== Nonsense Beat Session ===\n")
(doseq [{:keys [title voice speed make]} songs]
  (println (str "--- " title " ---"))
  (let [lines (make)]
    (doseq [line lines] (println (str "  " line)))
    (println)
    (perform
      (say voice (str title "!"))
      (pause 0.2)
      (with-speed speed
        (apply say voice (interpose "." lines)))
      (pause 0.2)
      (say voice (str (pick doubles) ", " (pick endings) "!"))
      (pause 0.5))))

;; Finale
(println "--- Grand Finale ---")
(perform
  (say M3 (str (pick call-outs) " " (pick call-outs)))
  (say F1 (str (pick triplets) ", " (pick triplets) ", " (pick quad-rhymes) "!"))
  (say M1 (str (pick doubles) ", " (pick doubles) ", " (pick doubles) "!"))
  (say M5 (breath) (str (pick triplets) ", " (pick endings) "!"))
  (say F4 (laugh) (str (pick doubles) ", " (pick endings) "!"))
  (pause 0.3)
  (say M3 "Plonk."))
